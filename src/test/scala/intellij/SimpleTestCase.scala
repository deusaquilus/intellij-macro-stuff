package intellij

import org.jetbrains.plugins.scala._
import com.intellij.lang.Language
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiComment, PsiElement, PsiFileFactory, PsiWhiteSpace}
import com.intellij.testFramework.fixtures._
import com.intellij.testFramework.{LightProjectDescriptor, UsefulTestCase}
import org.intellij.lang.annotations.{Language => InputLanguage}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectContext
//import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions

import scala.reflect.ClassTag

abstract class SimpleTestCase extends UsefulTestCase with intellij.MatcherAssertions {

  var fixture: CodeInsightTestFixture = _

  implicit def ctx: ProjectContext = fixture.getProject

  override def setUp(): Unit = {
    intellij.TestUtils.optimizeSearchingForIndexableFiles()
    super.setUp()
    fixture = createFixture()
    fixture.setUp()
  }

  protected def createFixture(): CodeInsightTestFixture = {
    val factory = IdeaTestFixtureFactory.getFixtureFactory
    val builder = factory.createLightFixtureBuilder(getProjectDescriptor, getTestName(false))
    factory.createCodeInsightFixture(builder.getFixture)
  }

  protected final def getProjectDescriptor: LightProjectDescriptor =
    new intellij.ScalaLightProjectDescriptor(sharedProjectToken)

  protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(this.getClass)

  override def tearDown(): Unit = try {
    fixture.tearDown()
  } finally {
    fixture = null
    super.tearDown()
  }

  def parseText(@InputLanguage("Scala")s: String, lang: Language): ScalaFile = {
    PsiFileFactory.getInstance(fixture.getProject)
      .createFileFromText("foo.scala", lang, s)
      .asInstanceOf[ScalaFile]
  }

  def parseText(@InputLanguage("Scala") s: String, enableEventSystem: Boolean = false): ScalaFile = {
    PsiFileFactory.getInstance(fixture.getProject)
      .createFileFromText("foo.scala", ScalaFileType.INSTANCE, s, System.currentTimeMillis(), enableEventSystem)
      .asInstanceOf[ScalaFile]
  }

  def parseText(@InputLanguage("Scala") s: String, caretMarker: String): (ScalaFile, Int) = {
    val trimmed = s.trim
    val caretPos = trimmed.indexOf(caretMarker)
    (parseText(trimmed.replaceAll(caretMarker, "")), caretPos)
  }

  implicit class Findable(private val element: ScalaFile) {
    def target: PsiElement = element.depthFirst()
      .dropWhile(!_.isInstanceOf[PsiComment])
      .drop(1)
      .dropWhile(_.isInstanceOf[PsiWhiteSpace])
      .next()
  }

  def describe(tree: PsiElement): String = toString(tree, 0)

  private def toString(root: PsiElement, level: Int): String = {
    val indent = List.fill(level)("  ").mkString
    val content = if (root.isInstanceOf[LeafPsiElement])
      "\"%s\"".format(root.getText) else root.getClass.getSimpleName
    val title = "%s%s\n".format(indent, content)
    title + root.children.map(toString(_, level + 1)).mkString
  }

  implicit class ScalaCode(@InputLanguage("Scala") private val s: String) {
    def stripComments: String =
      s.replaceAll("""(?s)/\*.*?\*/""", "")
        .replaceAll("""(?m)//.*$""", "")

    def parse: ScalaFile = parseText(s)

    def parse(lang: Language): ScalaFile = parseText(s, lang)

    def parseWithEventSystem: ScalaFile = parseText(s, enableEventSystem = true)

    def parse[T <: PsiElement: ClassTag]: T =
      parse.depthFirst().findByType[T].getOrElse {
        throw new RuntimeException("Unable to find PSI element with type " +
          implicitly[ClassTag[T]].runtimeClass.getSimpleName)
      }
  }

  case class ContainsPattern(fragment: String) {
    def unapply(s: String): Boolean = s.contains(fragment)
  }

  case class BundleMessagePattern(@Nls message: String) {
    def unapply(text: String): Boolean = text == message
  }
}