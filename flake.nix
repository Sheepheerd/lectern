{
  description = "My Android project";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs";
    unstable.url = "github:NixOS/nixpkgs/nixos-unstable";

    devshell.url = "github:numtide/devshell";
    flake-utils.url = "github:numtide/flake-utils";
    android.url = "github:tadfisher/android-nixpkgs";
  };

  outputs =
    {
      self,
      nixpkgs,
      devshell,
      flake-utils,
      android,
      unstable,
    }:
    {
      overlay = final: prev: {
        inherit (self.packages.${final.system}) android-sdk android-studio;
      };
    }
    // flake-utils.lib.eachSystem [ "aarch64-darwin" "x86_64-darwin" "x86_64-linux" ] (
      system:
      let
        inherit (nixpkgs) lib;
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          overlays = [
            devshell.overlays.default
            self.overlay
          ];
        };
      in
      {
        packages = {
          android-sdk = android.sdk.${system} (
            sdkPkgs:
            with sdkPkgs;
            [
              # Useful packages for building and testing.
              build-tools-36-0-0
              build-tools-35-0-0
              cmake-3-22-1
              cmdline-tools-latest
              emulator
              platform-tools
              platforms-android-36

              # Other useful packages for a development environment.
              ndk-27-1-12297006
              # ndk-26-1-10909125
              # skiaparser-3
              system-images-android-36-google-apis-playstore-arm64-v8a
              # sources-android-34
            ]
            ++ lib.optionals (system == "aarch64-darwin") [
              # system-images-android-34-google-apis-arm64-v8a
              # system-images-android-34-google-apis-playstore-arm64-v8a
            ]
            ++ lib.optionals (system == "x86_64-darwin" || system == "x86_64-linux") [
              # system-images-android-34-google-apis-x86-64
              # system-images-android-34-google-apis-playstore-x86-64
            ]
          );
        }
        // lib.optionalAttrs (system == "x86_64-linux") {
          # Android Studio in nixpkgs is currently packaged for x86_64-linux only.
          android-studio = pkgs.androidStudioPackages.stable;
          # android-studio = pkgs.androidStudioPackages.beta;
          # android-studio = pkgs.androidStudioPackages.preview;
          # android-studio = pkgs.androidStudioPackage.canary;
        };

        devShell = import ./devshell.nix {
          inherit pkgs;
          inherit unstable;
        };
      }
    );
}
