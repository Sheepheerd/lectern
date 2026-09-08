{ pkgs, unstable }:

with pkgs;

let
  unstable-pkgs = import unstable {
    inherit system;
    config.allowUnfree = true;
  };
  conditionalPackages = if pkgs.system != "aarch64-darwin" then [ android-studio ] else [ ];
in
with pkgs;

devshell.mkShell {
  name = "android-project";
  motd = ''
    Entered the Android app development environment.
  '';
  env = [
    {
      name = "ANDROID_HOME";
      value = "${android-sdk}/share/android-sdk";
    }
    {
      name = "ANDROID_SDK_ROOT";
      value = "${android-sdk}/share/android-sdk";
    }
    {
      name = "JAVA_HOME";
      value = jdk.home;
    }
  ];
  packages = [
    android-sdk
    gradle
    jdk17_headless

    nodejs_24
    yarn
    watchman
    prettier

    # unfreePkgs.antigravity
    # claude-code
    firebase-tools
    google-cloud-sdk
    unstable-pkgs.antigravity-cli

  ]
  ++ conditionalPackages;

  # Remember:
  # npx expo prebuild --clean
  # npx run app:android
  # remember api key
}
