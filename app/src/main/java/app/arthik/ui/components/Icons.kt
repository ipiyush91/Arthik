package app.arthik.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The handful of Material icons (Apache-2.0) the app needs, instead of the large icons artifact. */
object ArthikIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.White)).build()

    val Home = icon("home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val List = icon("list", "M3,13h2v-2H3v2zm0,4h2v-2H3v2zm0-8h2V7H3v2zm4,4h14v-2H7v2zm0,4h14v-2H7v2zM7,7v2h14V7H7z")
    val Wallet = icon("wallet", "M21,18v1c0,1.1-0.9,2-2,2H5c-1.11,0-2-0.9-2-2V5c0-1.1 0.89-2 2-2h14c1.1,0 2,0.9 2,2v1h-9c-1.11,0-2,0.9-2,2v8c0,1.1 0.89,2 2,2h9zM12,16h10V8H12v8zM16,13.5c-0.83,0-1.5-0.67-1.5-1.5s0.67-1.5 1.5-1.5 1.5,0.67 1.5,1.5-0.67,1.5-1.5,1.5z")
    val Insights = icon("insights", "M5,9.2h3V19H5zM10.6,5h2.8v14h-2.8zm5.6,8H19v6h-2.8z")
    val Settings = icon("settings", "M3,17v2h6v-2H3zM3,5v2h10V5H3zm10,16v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zm14,4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z")
    val Add = icon("add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Search = icon("search", "M15.5,14h-0.79l-0.28-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09-0.59 4.23-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99-5zm-6,0C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z")
    val Back = icon("back", "M20,11H7.83l5.59-5.59L12,4l-8,8 8,8 1.41-1.41L7.83,13H20v-2z")
    val Close = icon("close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    val ChevronLeft = icon("chevron_left", "M15.41,7.41L14,6l-6,6 6,6 1.41-1.41L10.83,12z")
    val ChevronRight = icon("chevron_right", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6-6z")
    val Check = icon("check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41-1.41z")
    val Filter = icon("filter", "M10,18h4v-2h-4v2zM3,6v2h18V6H3zm3,7h12v-2H6v2z")
}
