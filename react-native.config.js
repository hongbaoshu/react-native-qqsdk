module.exports = {
    dependency: {
      platforms: {
        ios: {
            "sharedLibraries": [
              "libz",
              "libiconv",
              "libsqlite3",
              "libstdc++",
              "Security",
              "SystemConfiguration",
              "CoreTelephony",
              "CoreGraphics"
            ]
          },
        android: {}, // projects are grouped into "platforms"
      },
    },
  };