allprojects {
  if (findProperty("otel.stable") != "true") {
    version = "1.10.0+ai.patches-alpha"
  } else {
    version = "1.10.0+ai.patches"
  }
}