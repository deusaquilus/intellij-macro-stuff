import org.jetbrains.plugins.scala.ScalaVersion

class ZioDirectMacroSupportTestScala3Lts extends BaseZioDirectMacroSupportTest {
  override def injectedScalaVersion = Some(ScalaVersion.Latest.Scala_3_LTS)
}
