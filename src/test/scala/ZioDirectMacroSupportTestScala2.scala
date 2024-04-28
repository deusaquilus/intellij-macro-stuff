import org.jetbrains.plugins.scala.ScalaVersion

class ZioDirectMacroSupportTestScala2 extends BaseZioDirectMacroSupportTest {

  override def injectedScalaVersion = Some(ScalaVersion.Latest.Scala_2_13)

  def test_defer_tpe(): Unit = {
    doTest("""defer.tpe { val a = ZIO.service[ConfigA].run.value; a }""", "ZIO[ConfigA, Nothing, java.lang.String")
  }
}
