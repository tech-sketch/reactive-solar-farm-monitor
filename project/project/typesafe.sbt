val rpUrl = "https://dl.bintray.com/typesafe/instrumented-reactive-platform"
val rpVersion = "15v09p01i03"

resolvers += "typesafe-rp-mvn" at rpUrl
resolvers += Resolver.url("typesafe-rp-ivy", url(rpUrl))(Resolver.ivyStylePatterns)
resolvers += "typesafe-rp-takipi" at "https://dl.bintray.com/takipi/maven"

addSbtPlugin("com.typesafe.rp" % "sbt-typesafe-rp" % rpVersion)