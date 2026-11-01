{
  description = "OpenCIE — unofficial CIE level 3 login from a computer (QR + NFC), arm64-v8a Android APK";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachSystem [ "x86_64-linux" "aarch64-linux" ] (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        lib = pkgs.lib;

        pname = "opencie";
        version = "0.1.0";
        jdk = pkgs.jdk21;
        gradle = pkgs.gradle_8;

        # Pure Kotlin app: no NDK, CMake or Flutter engine needed.
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "35" ];
          buildToolsVersions = [ "35.0.0" ];
          includeNDK = false;
          includeEmulator = false;
          includeSystemImages = false;
          includeSources = false;
        };
        androidSdk = androidComposition.androidsdk;
        sdkRoot = "${androidSdk}/libexec/android-sdk";
        buildToolsVersion = "35.0.0";

        # The CIE card layer comes from pagopa/cie-sdk-android (MIT), the SDK
        # the IO app uses: fetched at a pinned commit, patched with
        # ./patches/cie-sdk-android, and reduced to the card-protocol sources
        # that Gradle compiles into the app (OPENCIE_CIE_SDK_SRC).
        cieSdkRev = "5aed46485c20e6ce3a430dfec908c26a4831ff67"; # v0.1.11
        cieSdkPatched = pkgs.applyPatches {
          name = "cie-sdk-android-${builtins.substring 0 7 cieSdkRev}-opencie";
          src = pkgs.fetchFromGitHub {
            owner = "pagopa";
            repo = "cie-sdk-android";
            rev = cieSdkRev;
            hash = "sha256-Zj51DhiGuXUg0eK4O9oknCSwWu0k+YHt5hy8M0vFD9g=";
          };
          patches = lib.filesystem.listFilesRecursive ./patches/cie-sdk-android;
        };
        cieSdkFiles = [
          "CieLogger.kt"
          "Extensions.kt"
          "cie/ApduManager.kt"
          "cie/ApduResponse.kt"
          "cie/ApduSecureMessageManager.kt"
          "cie/Asn1Tag.kt"
          "cie/CieMethods.kt"
          "cie/OnTransmit.kt"
          "cie/ReadFileManager.kt"
          "cie/enums.kt"
          "cie/commands"
          "cie/key_store"
          "nfc/Algorithms.kt"
          "nfc/RSA.kt"
          "nfc/Sha256.kt"
          "nfc/Utils.kt"
        ];
        cieSdkSrc = pkgs.runCommand "cie-sdk-android-card-layer" { } ''
          from=${cieSdkPatched}/CieSDK/src/main/java/it/pagopa/io/app/cie
          to=$out/it/pagopa/io/app/cie
          for f in ${lib.escapeShellArgs cieSdkFiles}; do
            mkdir -p "$to/$(dirname "$f")"
            cp -r "$from/$f" "$to/$f"
          done
          install -Dm644 ${cieSdkPatched}/LICENSE "$out/LICENSE"
        '';

        src = lib.fileset.toSource {
          root = ./.;
          fileset = lib.fileset.unions [
            ./app
            ./build.gradle.kts
            ./settings.gradle.kts
            ./gradle.properties
          ];
        };

        reconstructMavenRepo = pkgs.writeShellApplication {
          name = "reconstruct-maven-repo";
          runtimeInputs = [
            pkgs.jq
            pkgs.coreutils
            pkgs.findutils
          ];
          text = ''
            src="$1"; out="$2"; mkdir -p "$out"
            find "$src" -type f | while read -r f; do
              rel="''${f#"$src"/}"
              g="$(echo "$rel" | cut -d/ -f1)"
              a="$(echo "$rel" | cut -d/ -f2)"
              v="$(echo "$rel" | cut -d/ -f3)"
              fn="$(echo "$rel" | cut -d/ -f5)"
              d="$out/''${g//.//}/$a/$v"
              mkdir -p "$d"
              cp -n "$f" "$d/$fn"
            done
            find "$out" -name '*.module' | while read -r m; do
              d="$(dirname "$m")"
              jq -r '.variants[]?.files[]? | "\(.name) \(.url)"' "$m" 2>/dev/null \
                | sort -u | while read -r name url; do
                [ -z "''${url:-}" ] && continue
                [ "$name" = "$url" ] && continue
                if [ -f "$d/$name" ] && [ ! -f "$d/$url" ]; then
                  # Urls may point at a sibling version (e.g. guava -jre → -android).
                  mkdir -p "$(dirname "$d/$url")"
                  cp "$d/$name" "$d/$url"
                fi
              done
            done
          '';
        };

        offlineInit = pkgs.writeText "nix-offline-init.gradle" ''
          def repoUrl = "file://${gradleRepo}"
          beforeSettings { settings ->
              settings.pluginManagement { repositories { maven { url repoUrl } } }
              if (settings.gradle.parent == null) {
                  settings.gradle.allprojects {
                      buildscript { repositories { maven { url repoUrl } } }
                      repositories { maven { url repoUrl } }
                  }
              } else {
                  settings.dependencyResolutionManagement {
                      repositories { maven { url repoUrl } }
                  }
              }
          }
        '';

        androidEnv = ''
          export ANDROID_HOME="${sdkRoot}"
          export ANDROID_SDK_ROOT="${sdkRoot}"
          export JAVA_HOME="${jdk.home}"
          export OPENCIE_CIE_SDK_SRC="${cieSdkSrc}"
          export GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2"
        '';

        mkScratch = ''
          SCRATCH="$TMPDIR/scratch"
          SHM_AVAILABLE_KIB="$(df -Pk /dev/shm 2>/dev/null | awk 'END { print $4 }')"
          if [ "''${SHM_AVAILABLE_KIB:-0}" -ge 8388608 ] \
            && mkdir -p "/dev/shm/${pname}-$$" 2>/dev/null; then
            SCRATCH="/dev/shm/${pname}-$$"
          fi
          mkdir -p "$SCRATCH"
          export HOME="$SCRATCH/home"
          export GRADLE_USER_HOME="$SCRATCH/gradle"
          mkdir -p "$HOME" "$GRADLE_USER_HOME"
        '';

        gradleOnline = "gradle --no-daemon --console=plain";
        gradleOffline = "gradle --offline --no-daemon --console=plain --init-script ${offlineInit}";

        # Every Maven artifact the build and the unit tests need, fetched once
        # and pinned by hash so the real builds below run fully offline.
        gradleRepo = pkgs.stdenvNoCC.mkDerivation {
          name = "${pname}-gradle-repo-${version}";
          inherit src;
          nativeBuildInputs = [
            jdk
            gradle
            androidSdk
            reconstructMavenRepo
          ];
          dontConfigure = true;
          buildPhase = ''
            runHook preBuild
            ${mkScratch}
            ${androidEnv}
            ${gradleOnline} assembleRelease assembleDebug testDebugUnitTest recordPaparazziDebug
            reconstruct-maven-repo \
              "$GRADLE_USER_HOME/caches/modules-2/files-2.1" "$out"
            runHook postBuild
          '';
          dontInstall = true;
          dontFixup = true;
          outputHashMode = "recursive";
          outputHashAlgo = "sha256";
          outputHash = "sha256-tstTDveXk+y0MU8l/XInKzAjOj11wqajWXZqd3PlDgY=";
        };

        mkGradleBuild =
          {
            name,
            tasks,
            installPhase,
            extraAttrs ? { },
          }:
          pkgs.stdenv.mkDerivation (
            {
              inherit name src;
              nativeBuildInputs = [
                jdk
                gradle
                androidSdk
              ];
              dontConfigure = true;
              buildPhase = ''
                runHook preBuild
                ${mkScratch}
                ${androidEnv}
                ${gradleOffline} ${tasks}
                runHook postBuild
              '';
              inherit installPhase;
              dontFixup = true;
            }
            // extraAttrs
          );

        # Unsigned release APK, signed later by CI (same contract as the example).
        apk = mkGradleBuild {
          name = "${pname}-${version}-arm64-v8a.apk";
          tasks = "assembleRelease";
          installPhase = ''
            runHook preInstall
            cp app/build/outputs/apk/release/app-release-unsigned.apk "$out"
            runHook postInstall
          '';
          extraAttrs = {
            __structuredAttrs = true;
            ci = [
              { action = "sign-apk"; }
              { action = "release"; }
            ];
            passthru = { inherit gradleRepo androidSdk; };
            meta = {
              description = "OpenCIE release APK (unsigned)";
              platforms = [
                "x86_64-linux"
                "aarch64-linux"
              ];
              license = lib.licenses.bsd3;
            };
          };
        };

        # Debug-signed APK that can be installed straight away for testing.
        apkDebug = mkGradleBuild {
          name = "${pname}-${version}-debug.apk";
          tasks = "assembleDebug";
          installPhase = ''
            runHook preInstall
            cp app/build/outputs/apk/debug/app-debug.apk "$out"
            runHook postInstall
          '';
          extraAttrs.meta.description = "OpenCIE debug APK (debug-signed, for testing)";
        };

        unitTests = mkGradleBuild {
          name = "${pname}-unit-tests-${version}";
          tasks = "testDebugUnitTest";
          installPhase = ''
            runHook preInstall
            mkdir -p "$out"
            cp -r app/build/test-results/testDebugUnitTest/. "$out/"
            runHook postInstall
          '';
          extraAttrs.meta.description = "OpenCIE JVM unit tests";
        };

        # Paparazzi renders every screen (light/dark) on the JVM, no device.
        # The images are published like the example's web screenshots.
        screenshots = mkGradleBuild {
          name = "${pname}-screenshots-${version}";
          tasks = "recordPaparazziDebug";
          installPhase = ''
            runHook preInstall
            mkdir -p "$out"
            for f in app/src/test/snapshots/images/*.png; do
              n="''${f##*_ScreenshotTest_}"
              install -m644 "$f" "$out/${pname}-''${n#*_}"
            done
            runHook postInstall
          '';
          extraAttrs.meta.description = "OpenCIE screen renders (Paparazzi)";
        };

        # Fails when the UI no longer matches app/src/test/snapshots/images.
        screenshotCheck = mkGradleBuild {
          name = "${pname}-screenshot-check-${version}";
          tasks = "verifyPaparazziDebug";
          installPhase = ''
            runHook preInstall
            mkdir -p "$out"
            cp -r app/build/reports/paparazzi/. "$out/" 2>/dev/null || true
            runHook postInstall
          '';
          extraAttrs.meta.description = "OpenCIE screenshot regression check";
        };

        screenshotNames =
          lib.concatMap
            (s: [
              "${s}-light"
              "${s}-dark"
            ])
            [
              "home"
              "pin"
              "hold-card"
              "code"
              "error"
            ];
        # flake-ci release outputs are individual regular files.
        screenshotArtifact =
          n:
          pkgs.runCommand "${pname}-${n}.png" {
            __structuredAttrs = true;
            ci = [ { action = "release"; } ];
            meta.description = "OpenCIE screenshot release artifact: ${n}";
          } "cp ${screenshots}/${pname}-${n}.png \"$out\"";

        installDebug = pkgs.writeShellApplication {
          name = "opencie-install";
          text = ''
            exec ${sdkRoot}/platform-tools/adb install -r ${apkDebug} "$@"
          '';
        };
      in
      {
        packages = {
          default = apk;
          inherit
            apk
            gradleRepo
            androidSdk
            screenshots
            cieSdkSrc
            ;
          apk-debug = apkDebug;
        }
        // lib.listToAttrs (
          map (n: lib.nameValuePair "screenshot-${n}" (screenshotArtifact n)) screenshotNames
        );
        checks = {
          unit-tests = unitTests;
          screenshots = screenshotCheck;
        };
        apps.default = {
          type = "app";
          program = "${installDebug}/bin/opencie-install";
          meta.description = "Install the OpenCIE debug APK on the connected device via adb";
        };
        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            gradle
            androidSdk
          ];
          shellHook = androidEnv;
        };
        formatter = pkgs.nixfmt;
      }
    );
}
