package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import junit.framework.TestCase
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Message, ScalaAnnotationHolder, ScalaAnnotator}
import org.jetbrains.plugins.scala.base.{FailableTest, ScalaSdkOwner}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.{ProjectContext, ScalaFeatures}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment
import org.jetbrains.plugins.scala.util.assertions.PsiAssertions.assertNoParserErrors
import org.junit.Assert._

trait TypeInferenceDoTest extends TestCase with FailableTest with ScalaSdkOwner {
  import Message._

  protected val START = "/*start*/"
  protected val END = "/*end*/"

  private val ExpectedPattern = """expected: (.*)""".r
  private val SimplifiedPattern = """simplified: (.*)""".r
  private val JavaTypePattern = """java type: (.*)""".r

  //Used in expected data, some types may be rendered in different ways, e.g. `A with B` or `B with A`
  private val FewVariantsMarker = "Few variants:"

  def configureFromFileText(fileName: String, fileText: Option[String]): ScalaFile

  final protected def doTest(fileText: String, expectedType: String): Unit = {
    doTest(fileText, expectedType: String, true)
  }

  final protected def doTest(
    fileText: String,
    expectedType: String,
    failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail: Boolean
  ): Unit = {
    doTestInternal(
      fileText,
      expectedType,
      failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail = failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail
    )
  }

  protected def doTestInternal(
    fileText: String,
    expectedType: String,
    failOnParserErrorsInFile: Boolean = true,
    failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail: Boolean = true,
    fileName: String = "dummy.scala"
  ): Unit = {
    val fullFileText =
     s"""|import zio._
         |import zio.direct._
         |
         |${fileText}
         |""".stripMargin
    val scalaFile: ScalaFile = configureFromFileText(fileName, Some(fullFileText))
    if (failOnParserErrorsInFile) {
      assertNoParserErrors(scalaFile)
    }
    if (!shouldPass && failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail) {
      val errors = errorsFromAnnotator(scalaFile)
      assertTrue(
        s"""Expected to detect annotator errors in available test, but found no errors.
           |Maybe the test was fixed in some changes?
           |Check it manually and consider moving into successfully tests.
           |If the test is still actual but no annotator errors is expected then pass argument `failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail=true`""".stripMargin,
        errors.nonEmpty
      )
    }

    val expr: ScExpression = findLastExpression(scalaFile)
    implicit val tpc: TypePresentationContext = TypePresentationContext.emptyContext
    val typez = expr.`type`() match {
      case Right(t) if t.isUnit => expr.getTypeIgnoreBaseType
      case x => x
    }
    typez match {
      case Right(ttypez) =>
        val res = ttypez.presentableText
        //val ExpectedResultFromLastComment(_, lastLineCommentText) = TestUtils.extractExpectedResultFromLastComment(scalaFile)
        val expectedTextForCurrentVersion = extractTextForCurrentVersion(expectedType, version)

        if (expectedTextForCurrentVersion.startsWith(FewVariantsMarker)) {
          val results = expectedTextForCurrentVersion.substring(FewVariantsMarker.length).trim.split('\n')
          if (!results.contains(res))
            assertEqualsFailable(results(0), res)
        }
        else expectedTextForCurrentVersion match {
          case ExpectedPattern(expectedExpectedTypeText) =>
            val actualExpectedTypeText = expr.expectedType().map(_.presentableText).getOrElse("<none>")
            assertEqualsFailable(expectedExpectedTypeText, actualExpectedTypeText)
          case SimplifiedPattern(expectedText) =>
            assertEqualsFailable(expectedText, TypePresentation.withoutAliases(ttypez))
          case JavaTypePattern(expectedText) =>
            assertEqualsFailable(expectedText, expr.`type`().map(_.toPsiType.getPresentableText()).getOrElse("<none>"))
          case _ =>
            assertEqualsFailable(expectedTextForCurrentVersion, res)
        }
      case Failure(msg) if shouldPass => fail(msg)
      case _ =>
    }
  }

  protected def findSelectedExpression(scalaFile: ScalaFile): ScExpression = {
    val fileText = scalaFile.getText

    val startMarkerOffset = fileText.indexOf(START)
    val startOffset = startMarkerOffset + START.length
    assert(startMarkerOffset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")

    val endOffset = fileText.indexOf(END)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val elementAtOffset = PsiTreeUtil.getParentOfType(scalaFile.findElementAt(startOffset), classOf[ScExpression])
    val addOne = if (elementAtOffset != null) 0 else 1 //for xml tests
    val expr: ScExpression = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset + addOne, endOffset, classOf[ScExpression])
    assert(expr != null, "Not specified expression in range to infer type.")

    expr
  }

  protected def findLastExpression(scalaFile: ScalaFile): ScExpression = {
//    val fileText = scalaFile.getText
//
//    val startMarkerOffset = fileText.indexOf(START)
//    val startOffset = startMarkerOffset + START.length
//    assert(startMarkerOffset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
//
//    val endOffset = fileText.indexOf(END)
//    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")
//
//
//
//    val elementAtOffset = PsiTreeUtil.getParentOfType(scalaFile.findElementAt(startOffset), classOf[ScExpression])
//    val addOne = if (elementAtOffset != null) 0 else 1 //for xml tests
//    val expr: ScExpression = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset + addOne, endOffset, classOf[ScExpression])

    val expr =
      scalaFile.getChildren.lastOption match {
        case Some(scExpr: ScExpression) => scExpr
        case Some(other) =>
          fail("Invalid last child (is not an ScExpression): " + other.getText).asInstanceOf[Nothing]
        case None =>
          fail("=== Not specified expression in the content ===\n" + scalaFile.getText).asInstanceOf[Nothing]
      }

    expr
  }

  protected def parseExpressionFromType(expectedType: String)(implicit pc: ProjectContext): ScTypeElement = {
    ScalaPsiElementFactory.createTypeElementFromText(expectedType, ScalaFeatures.default)
  }

  private val VersionPrefixRegex = """^\[Scala_([\w\d_]*)\](.*)""".r

  // formats:
  // 2_12 => 2.12.MAX_VERSION,
  // 2_12_7 => 2.12.7
  private def selectVersion(versionStr: String): Option[ScalaVersion] = {
    val versionStrWithDots = versionStr.replace('_', '.')
    ScalaSdkOwner.allTestVersions.find(_.minor == versionStrWithDots)
      .orElse(ScalaSdkOwner.allTestVersions.filter(_.major == versionStrWithDots).lastOption)
  }

  private def extractTextForCurrentVersion(text: String, version: ScalaVersion): String = {
    val lines = text.split('\n')
    val ((lastVer, lastText), resultListWithoutLast) = lines
      .foldLeft(((Option.empty[ScalaVersion], ""), Seq.empty[(Option[ScalaVersion], String)])) {
        case (((curver, curtext), result), line) =>
          val foundVersion = line match {
            case VersionPrefixRegex(versionStr, tail) => selectVersion(versionStr.trim).map((_, tail))
            case _                                    => None
          }
          foundVersion match {
            case Some((v, lineTail)) => (Some(v), lineTail) -> (result :+ (curver -> curtext))
            case None                => (curver, if (curtext.isEmpty) line else curtext + "\n" + line) -> result
          }
      }

    val resultList = resultListWithoutLast :+ (lastVer -> lastText)
    if (resultList.length == 1) {
      resultList.head._2
    } else {
      val resultsWithVersions = resultList
        .flatMap {
          case (Some(v), text) => Some(v -> text)
          case (None, text) => Some(ScalaSdkOwner.allTestVersions.head -> text)
        }

      assert(resultsWithVersions.map(_._1).sliding(2).forall { case Seq(a, b) => a < b})

      resultsWithVersions.zip(resultsWithVersions.tail)
        .find { case ((v1, _), (v2, _)) => v1 <= version && version < v2 }
        .map(_._1._2)
        .getOrElse(resultsWithVersions.last._2)
    }
  }

  private def errorsFromAnnotator(file: PsiFile): Seq[Message] = {
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().foreach(annotate(_))

    val annotations = mock.annotations
    val errors = annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, _) => true
      case _ => false
    }
    errors
  }

  private def annotate(element: PsiElement)(implicit holder: ScalaAnnotationHolder): Unit =
    new ScalaAnnotator().annotate(element)
}
