package com.pinakes.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.pinakes.app.data.model.StandaloneArticle
import com.pinakes.app.ui.screens.periodicals.StandaloneArticleContent
import com.pinakes.app.ui.theme.PinakesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StandaloneArticleUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun publishedActionsOpenThePdfResourceAndWebsite() {
        var pdf = 0; var resource = 0; var web = 0
        compose.setContent { PinakesTheme {
            StandaloneArticleContent(
                article = StandaloneArticle(title = "Article", subtitle = "Article subtitle",
                    hasPublicPdf = true, pdfUrl = "https://library.example/42/pdf",
                    hasPublicResource = true, resourceAddress = "https://archive.example/42",
                    resourceLabel = "Archive copy", resourceAccess = "Reading room use"),
                onOpenPdf = { pdf++ }, onOpenPeriodical = {}, onOpenIssue = {},
                onOpenResource = { resource++ }, onOpenWeb = { web++ },
            )
        } }
        compose.onNodeWithText("Article subtitle").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.periodicals_open_pdf)).performScrollTo().performClick()
        compose.onNodeWithText("Archive copy").performScrollTo().performClick()
        compose.onNodeWithText("Reading room use").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.standalone_article_open_web)).performScrollTo().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(1, 1, 1), listOf(pdf, resource, web)) }
    }

    @Test fun unpublishedResourcesAndPdfsHaveNoActionsOrAccessNotes() {
        compose.setContent { PinakesTheme {
            StandaloneArticleContent(
                article = StandaloneArticle(title = "Article", pdfUrl = "https://library.example/private/pdf",
                    resourceAddress = "https://archive.example/private", resourceLabel = "Private copy",
                    resourceAccess = "Private access note"),
                onOpenPdf = {}, onOpenPeriodical = {}, onOpenIssue = {},
            )
        } }
        compose.onNodeWithText(text(R.string.periodicals_open_pdf)).assertDoesNotExist()
        compose.onNodeWithText("Private copy").assertDoesNotExist()
        compose.onNodeWithText("Private access note").assertDoesNotExist()
    }

    @Test fun publishedLocalReferenceIsTextWithItsAccessConditions() {
        compose.setContent { PinakesTheme {
            StandaloneArticleContent(
                article = StandaloneArticle(title = "Article", hasPublicResource = true,
                    resourceAddress = "archive/1988/petersen.pdf", resourceAccess = "In library only"),
                onOpenPdf = {}, onOpenPeriodical = {}, onOpenIssue = {},
            )
        } }
        compose.onNodeWithText("archive/1988/petersen.pdf").performScrollTo().assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("In library only").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.standalone_article_online_resource)).assertDoesNotExist()
    }
}
