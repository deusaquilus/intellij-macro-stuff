package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.util.{PsiFileTestUtil, TestUtils}

import java.io.File

abstract class TypeInferenceTestBase extends ScalaLightCodeInsightFixtureTestCase with TypeInferenceDoTest {

  override val START = "/*start*/"
  override val END = "/*end*/"

  protected def folderPath: String = TestUtils.getTestDataPath + "/typeInference/"

  override protected def sharedProjectToken = SharedTestProjectToken(this.getClass)

  protected def doInjectorTest(injector: SyntheticMembersInjector): Unit = {
    val extensionArea = ApplicationManager.getApplication.getExtensionArea
    val extensionPoint = extensionArea.getExtensionPoint(SyntheticMembersInjector.EP_NAME)
    extensionPoint.registerExtension(injector, getTestRootDisposable)
    doTest()
  }

  override def configureFromFileText(fileName: String, fileText: Option[String]): ScalaFile = {
    val text = fileText.getOrElse {
      val filePath = folderPath + fileName
      val ioFile: File = new File(filePath)
      FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    }
    configureFromFileText(ScalaFileType.INSTANCE, StringUtil.convertLineSeparators(text.trim))
    getFile.asInstanceOf[ScalaFile]
  }

  protected def addFileToProject(fileName: String, text: String): PsiFile =
    PsiFileTestUtil.addFileToProject(fileName, text, getProject)

  protected def doTest(): Unit = {
    val fileName = getTestName(false) + ".scala"
    doTest(None, fileName = fileName)
  }
}