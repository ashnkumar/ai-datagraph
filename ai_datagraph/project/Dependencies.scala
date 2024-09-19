import sbt._

object Dependencies {

  object V {
    val tessellation = "2.8.1"
    val decline = "2.4.1"
    val cats = "2.9.0"
    val catsEffect = "3.4.2"
    val organizeImports = "0.5.0"
    val weaver = "0.8.1"
    val pureconfig = "0.17.4"
    val http4s = "0.23.16"
  }
  def tessellation(artifact: String): ModuleID = "org.constellation" %% s"tessellation-$artifact" % V.tessellation

  def decline(artifact: String = ""): ModuleID =
    "com.monovore" %% {
      if (artifact.isEmpty) "decline" else s"decline-$artifact"
    } % V.decline

  def http4s(artifact: String): ModuleID = "org.http4s" %% s"http4s-$artifact" % V.http4s
    
  object Libraries {
    val tessellationNodeShared = tessellation("node-shared")
    val tessellationCurrencyL0 = tessellation("currency-l0")
    val tessellationCurrencyL1 = tessellation("currency-l1")
    val declineCore = decline()
    val declineEffect = decline("effect")
    val declineRefined = decline("refined")
    val cats = "org.typelevel" %% "cats-core" % V.cats
    val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect
    val catsEffectTestkit = "org.typelevel" %% "cats-effect-testkit" % V.catsEffect
    val weaverCats = "com.disneystreaming" %% "weaver-cats" % V.weaver
    val weaverDiscipline = "com.disneystreaming" %% "weaver-discipline" % V.weaver
    val weaverScalaCheck = "com.disneystreaming" %% "weaver-scalacheck" % V.weaver
    val organizeImports = "com.github.liancheng" %% "organize-imports" % V.organizeImports
    val pureconfigCore = "com.github.pureconfig" %% "pureconfig" % V.pureconfig
    val pureconfigCats = "com.github.pureconfig" %% "pureconfig-cats-effect" % V.pureconfig
    val http4sCore = http4s("core")
    val http4sDsl = http4s("dsl")
    val http4sServer = http4s("ember-server")
    val http4sClient = http4s("ember-client")
    val http4sCirce = http4s("circe")    
    val doobieCore = "org.tpolecat" %% "doobie-core" % "1.0.0-RC2"
    val doobieHikari = "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC2"
    val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC2"
    val postgres = "org.postgresql" % "postgresql" % "42.2.23"    
  }

  // Scalafix rules
  val organizeImports = "com.github.liancheng" %% "organize-imports" % "0.5.0"

  object CompilerPlugin {

    val betterMonadicFor = compilerPlugin(
      "com.olegpy" %% "better-monadic-for" % "0.3.1"
    )

    val kindProjector = compilerPlugin(
      ("org.typelevel" % "kind-projector" % "0.13.2").cross(CrossVersion.full)
    )

    val semanticDB = compilerPlugin(
      ("org.scalameta" % "semanticdb-scalac" % "4.7.1").cross(CrossVersion.full)
    )
  }
}
