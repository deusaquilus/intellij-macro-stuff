import org.jetbrains.plugins.scala.ScalaVersion

class ZioDirectMacroSupportTestScala3Latest extends BaseZioDirectMacroSupportTest {
  override def injectedScalaVersion = Some(ScalaVersion.Latest.Scala_3_4)
}
