package com.wizaird.app.ui

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.wizaird.app.R
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j

/**
 * A selectable markdown text component that uses AndroidView + TextView + Markwon.
 * This allows adding custom actions to the text selection toolbar.
 *
 * @param markdown The markdown text to display
 * @param style The text style to apply
 * @param modifier Modifier for the composable
 * @param onAddToGlossary Callback when "Add to Glossary" is selected with the selected text
 */
@Composable
fun SelectableMarkdownText(
    markdown: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onAddToGlossary: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Get the typeface for the font
    val typeface = remember(style.fontFamily) {
        when (style.fontFamily) {
            com.wizaird.app.ui.theme.PixeloidFont -> 
                ResourcesCompat.getFont(context, R.font.pixeloid_sans)
            com.wizaird.app.ui.theme.MinecraftFont -> 
                ResourcesCompat.getFont(context, R.font.macs_minecraft)
            com.wizaird.app.ui.theme.MinecraftBoldFont -> 
                ResourcesCompat.getFont(context, R.font.macs_minecraft_bold)
            com.wizaird.app.ui.theme.PixelFont -> 
                ResourcesCompat.getFont(context, R.font.vt323)
            else -> Typeface.DEFAULT
        }
    }
    
    // Calculate text size in pixels
    val textSizePx = remember(style.fontSize) {
        style.fontSize.value * context.resources.displayMetrics.scaledDensity
    }
    
    // Create Coil ImageLoader for loading images in markdown
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
    
    // Create Prism4j instance for syntax highlighting
    val prism4j = remember { Prism4j(GrammarLocatorDef()) }
    
    // Create Markwon instance with all plugins and custom configuration
    val markwon = remember(typeface, style, imageLoader) {
        Markwon.builder(context)
            // Tables support
            .usePlugin(TablePlugin.create(context))
            // Strikethrough support (~~text~~)
            .usePlugin(StrikethroughPlugin.create())
            // Task lists support (- [ ] and - [x])
            .usePlugin(TaskListPlugin.create(context))
            // Syntax highlighting for code blocks
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
            // Image loading with Coil
            .usePlugin(CoilImagesPlugin.create(context, imageLoader))
            // Linkify - make URLs, emails, phone numbers clickable
            .usePlugin(LinkifyPlugin.create())
            // HTML support
            .usePlugin(HtmlPlugin.create())
            // Custom theme configuration
            .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    // Only set codeTypeface — setting headingTypeface with a custom font
                    // prevents Android's bold synthesis from working on headers,
                    // because an explicit typeface object overrides the StyleSpan(BOLD).
                    // Bold synthesis (same as **bold** text) works automatically when
                    // headingTypeface is left unset.
                    //
                    // Use the system monospace font for code blocks so they render
                    // with a standard code font instead of the pixel/Minecraft font.
                    builder.codeTypeface(Typeface.MONOSPACE)
                    builder.headingTextSizeMultipliers(floatArrayOf(1.5f, 1.3f, 1.15f, 1f, 1f, 1f))
                    builder.linkColor(0xFF6B9BD1.toInt())
                    builder.codeBackgroundColor(0x20FFFFFF)
                    // Blockquote styling
                    builder.blockQuoteColor(0xFFAAAAAA.toInt())
                }
            })
            .build()
    }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                // Make text selectable
                setTextIsSelectable(true)
                
                // Required for Markwon bullet/list spans to render correctly
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                
                // Make background transparent
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                // Apply base text properties BEFORE setting content
                textSize = style.fontSize.value
                setTextColor(style.color.toArgb())
                typeface?.let { 
                    setTypeface(it)
                    // Force typeface by setting it as default
                    paint.typeface = it
                }
                letterSpacing = 0f
                // Line spacing multiplier 1.2
                setLineSpacing(0f, 1.2f)
                includeFontPadding = false
                
                // Set custom action mode callback for text selection
                customSelectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        // Add custom "Add to Glossary" menu item.
                        // The floating toolbar ignores setIcon(), so we use a sparkle
                        // unicode character as a visual AI indicator in the label itself.
                        if (onAddToGlossary != null) {
                            menu?.add(0, MENU_ITEM_ADD_TO_GLOSSARY, 0, "✨ Add to Glossary")
                        }
                        return true
                    }
                    
                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        // Remove the system-injected "AI Writing Assist" option if present.
                        // It is added by Google's text toolbar service under various titles
                        // depending on locale and Android version.
                        var changed = false
                        val toRemove = mutableListOf<Int>()
                        for (i in 0 until (menu?.size() ?: 0)) {
                            val item = menu?.getItem(i) ?: continue
                            val title = item.title?.toString()?.lowercase() ?: continue
                            if (title.contains("writing") || title.contains("ai writing")) {
                                toRemove.add(item.itemId)
                            }
                        }
                        toRemove.forEach { id ->
                            menu?.removeItem(id)
                            changed = true
                        }
                        return changed
                    }
                    
                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        return when (item?.itemId) {
                            MENU_ITEM_ADD_TO_GLOSSARY -> {
                                // Get selected text
                                val start = selectionStart
                                val end = selectionEnd
                                val selectedText = text.substring(start, end)
                                
                                // Invoke callback
                                onAddToGlossary?.invoke(selectedText)
                                
                                // Close action mode
                                mode?.finish()
                                true
                            }
                            else -> false
                        }
                    }
                    
                    override fun onDestroyActionMode(mode: ActionMode?) {
                        // Cleanup if needed
                    }
                }
            }
        },
        update = { textView ->
            // Set markdown content
            markwon.setMarkdown(textView, markdown)
            
            // Only reapply properties that DON'T wipe Markwon's spans.
            // setTypeface() must NOT be called here — it resets all spans
            // (bold, headers, bullets) that Markwon just applied.
            textView.apply {
                textSize = style.fontSize.value
                setTextColor(style.color.toArgb())
                letterSpacing = 0f
                setLineSpacing(0f, 1.2f)
                includeFontPadding = false
                invalidate()
                requestLayout()
            }
        }
    )
}

private const val MENU_ITEM_ADD_TO_GLOSSARY = 1001

/**
 * Grammar locator for Prism4j syntax highlighting.
 * Provides syntax highlighting support for common programming languages.
 */
class GrammarLocatorDef : io.noties.prism4j.GrammarLocator {
    override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? {
        // Return null for all languages - this will fall back to basic code block rendering
        // without syntax highlighting. This avoids dependency issues with Prism4j language packs.
        return null
    }

    override fun languages(): MutableSet<String> {
        return mutableSetOf()
    }
}
