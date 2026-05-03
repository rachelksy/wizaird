package com.wizaird.app.ui

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
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
import com.wizaird.app.R
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme

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
    
    // Create Markwon instance with custom configuration to match our text style
    val markwon = remember(typeface, style) {
        Markwon.builder(context)
            .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    // Only set codeTypeface — setting headingTypeface with a custom font
                    // prevents Android's bold synthesis from working on headers,
                    // because an explicit typeface object overrides the StyleSpan(BOLD).
                    // Bold synthesis (same as **bold** text) works automatically when
                    // headingTypeface is left unset.
                    typeface?.let { tf ->
                        builder.codeTypeface(tf)
                    }
                    builder.headingTextSizeMultipliers(floatArrayOf(1.5f, 1.3f, 1.15f, 1f, 1f, 1f))
                    builder.linkColor(0xFF6B9BD1.toInt())
                    builder.codeBackgroundColor(0x20FFFFFF)
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
                        // Add custom "Add to Glossary" menu item
                        if (onAddToGlossary != null) {
                            menu?.add(0, MENU_ITEM_ADD_TO_GLOSSARY, 0, "Add to Glossary")
                        }
                        return true
                    }
                    
                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return false
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
