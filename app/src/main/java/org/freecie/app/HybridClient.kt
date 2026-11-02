package org.freecie.app

import it.pagopa.io.app.cie.cie.key_store.CieProvider
import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.TlsVersion
import org.freecie.app.diagnostics.Diagnostics
import org.freecie.app.diagnostics.Kind
import org.freecie.app.diagnostics.Redact
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyStore
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Sends the signed QR to the identity server over mutual TLS. The client key
 * lives on the card: pagoPA's `CieProvider` routes the TLS handshake
 * signature to the connected card via `CieSigner` (see patches/), so the card must stay on
 * the phone until [send] returns.
 */
class HybridClient(private val request: QrLoginRequest, private val certificate: ByteArray) {

    class HttpError(val code: Int) : Exception("HTTP $code")

    /** Blocking; call it on the NFC thread that owns the card session. */
    fun send(signature: ByteArray): HybridAuth.Result {
        val url = request.idpBaseUrl + HybridAuth.ENDPOINT
        val form = HybridAuth.formBody(request, signature)
        val call = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        Diagnostics.add(Kind.HTTP, "POST $url")
        Diagnostics.add(
            Kind.HTTP,
            "  modulo: hashJsonFirmato=<${signature.size * 2} caratteri esadecimali>, " +
                "opId=<${request.opId.length} caratteri>; TLS 1.2 con il certificato della carta come certificato client",
        )
        val started = System.nanoTime()
        try {
            client().newCall(call).execute().use { response ->
                val millis = (System.nanoTime() - started) / 1_000_000
                val text = response.body?.string().orEmpty()
                Diagnostics.add(
                    if (response.isSuccessful) Kind.HTTP else Kind.ERROR,
                    "← HTTP ${response.code} ${response.message} in $millis ms, " +
                        "${response.header("Content-Type") ?: "nessun Content-Type"}, ${text.length} caratteri",
                )
                response.handshake?.let { handshake ->
                    Diagnostics.add(
                        Kind.HTTP,
                        "  TLS ${handshake.tlsVersion.javaName} ${handshake.cipherSuite.javaName}; " +
                            "certificato del server ${commonName(handshake.peerCertificates.firstOrNull(), subject = true)} " +
                            "emesso da ${commonName(handshake.peerCertificates.firstOrNull(), subject = false)}; " +
                            "certificato client inviato: ${if (handshake.localCertificates.isNotEmpty()) "sì" else "NO"}",
                    )
                }
                Diagnostics.add(Kind.HTTP, "  corpo: ${Redact.responseBody(text)}")
                if (!response.isSuccessful) throw HttpError(response.code)
                return HybridAuth.parseResponse(text)
            }
        } catch (e: IOException) {
            val millis = (System.nanoTime() - started) / 1_000_000
            Diagnostics.add(Kind.ERROR, "✘ POST $url fallita dopo $millis ms: ${Redact.throwable(e)}")
            throw e
        }
    }

    private fun commonName(certificate: Certificate?, subject: Boolean): String {
        val x509 = certificate as? X509Certificate ?: return "?"
        val principal = if (subject) x509.subjectX500Principal else x509.issuerX500Principal
        return principal.name.split(',').map { it.trim() }.firstOrNull { it.startsWith("CN=") }
            ?.removePrefix("CN=")?.let { "\"$it\"" } ?: "?"
    }

    private fun client(): OkHttpClient {
        // Same set-up as the official client: card-backed key manager,
        // platform trust store, TLS 1.2 (the card cannot do the RSA-PSS
        // signatures that TLS 1.3 requires).
        if (Security.getProvider(CieProvider.PROVIDER_NAME) == null) Security.insertProviderAt(CieProvider(), 1)

        val keyStore = KeyStore.getInstance(CieProvider.KEYSTORE_TYPE)
        keyStore.load(ByteArrayInputStream(certificate), null)
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, null) }
            .keyManagers

        val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .single() as X509TrustManager

        val tls = SSLContext.getInstance("TLSv1.2").apply { init(keyManagers, arrayOf(trustManager), null) }

        return OkHttpClient.Builder()
            .sslSocketFactory(tls.socketFactory, trustManager)
            .connectionSpecs(
                listOf(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).tlsVersions(TlsVersion.TLS_1_2).build()),
            )
            .eventListener(NetworkTrace)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

}

/** DNS, connection and TLS milestones for the diagnostic log (public data only). */
private object NetworkTrace : EventListener() {
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        Diagnostics.add(Kind.HTTP, "  DNS $domainName → ${inetAddressList.joinToString { it.hostAddress ?: "?" }}")
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        Diagnostics.add(Kind.HTTP, "  connessione a ${inetSocketAddress.address?.hostAddress}:${inetSocketAddress.port}")
    }

    override fun secureConnectStart(call: Call) {
        Diagnostics.add(Kind.HTTP, "  handshake TLS avviato (lo firma la carta)")
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        Diagnostics.add(Kind.HTTP, "  handshake TLS completato: ${handshake?.tlsVersion?.javaName ?: "?"}")
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        Diagnostics.add(Kind.ERROR, "  connessione a ${inetSocketAddress.address?.hostAddress} fallita: ${Redact.throwable(ioe)}")
    }
}
