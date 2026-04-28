# Pixel Shape Corner Fix

## Problem
Your pixel-style shapes were painting corners with a solid `cutColor` instead of making them transparent. This caused three major issues:

1. **No true transparency** - corners showed the background color, not what's actually behind them
2. **Shadows didn't work** - you'd see a rectangular shadow box instead of the pixel shape
3. **Overlay nightmare** - placing shapes on different backgrounds required changing `cutColor` every time

## Solution
Updated all pixel shape drawing functions to use `BlendMode.Clear` for truly transparent corners:

### Changes Made

1. **PixelBox** - All corner styles (Cut, Rounded, Rounded8, Circle, XLargeCircle) now use transparent corners
2. **drawPixelCircle** - Corners are now transparent
3. **drawPixelArrowButton** - Corners are now transparent  
4. **Added `graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }`** - Required for BlendMode.Clear to work

### How It Works

**Before:**
```kotlin
drawRect(cutColor, Offset(0f, 0f), Size(p*5, p))  // Painted with solid color
```

**After:**
```kotlin
drawRect(Color.Transparent, Offset(0f, 0f), Size(p*5, p), blendMode = BlendMode.Clear)  // Truly transparent
```

## Adding Shadows

Now that corners are transparent, you can add pixel-perfect shadows using the new `pixelShadow()` modifier:

### Usage

```kotlin
// Add shadow to a PixelBox
PixelBox(
    modifier = Modifier
        .pixelShadow(
            shape = PixelRounded8Shape,  // Match your corner style
            offsetY = 4.dp,
            color = Color.Black.copy(alpha = 0.3f)
        ),
    fillColor = colors.secondarySurface,
    cornerStyle = PixelCornerStyle.Rounded8
) {
    // content
}

// Add shadow to a button with drawPixelArrowButton
Box(
    modifier = Modifier
        .size(32.dp)
        .pixelShadow(PixelRounded8Shape, offsetY = 2.dp)
        .drawPixelArrowButton(
            fillColor = Coral,
            cutColor = colors.background,  // Not used anymore, but kept for API compatibility
            arrowColor = colors.secondaryIcon
        )
)

// Add shadow to a circle button
Box(
    modifier = Modifier
        .size(40.dp)
        .pixelShadow(PixelCircleButtonShape, offsetY = 3.dp)
        .drawPixelCircle(
            fillColor = colors.secondaryButton,
            borderColor = Color.Transparent,
            cutColor = colors.background  // Not used anymore
        )
)
```

### Available Shadow Shapes

Match these to your corner styles:

- `PixelRounded8Shape` - for `PixelCornerStyle.Rounded8` and `drawPixelArrowButton`
- `PixelRoundedShape` - for `PixelCornerStyle.Rounded`
- `PixelCircleButtonShape` - for `drawPixelCircle` (40dp circles)
- `PixelLargeCircleShape` - for `PixelCornerStyle.Circle` (64dp circles)
- `PixelXLargeCircleShape` - for `PixelCornerStyle.XLargeCircle` (80dp circles)

### Shadow Parameters

```kotlin
fun Modifier.pixelShadow(
    shape: Shape,                                    // The pixel shape to match
    color: Color = Color.Black.copy(alpha = 0.25f), // Shadow color
    offsetX: Dp = 0.dp,                             // Horizontal offset
    offsetY: Dp = 4.dp,                             // Vertical offset (drop shadow)
    blur: Dp = 0.dp                                 // Blur radius (not implemented yet)
)
```

## Benefits

✅ **Truly transparent corners** - shapes work on any background  
✅ **Pixel-perfect shadows** - shadows follow the exact pixel shape  
✅ **Easy overlays** - no more background color matching nightmares  
✅ **Consistent API** - same approach for all pixel shapes  

## Example: Popup Menu with Shadow

```kotlin
Box(
    modifier = Modifier
        .pixelShadow(
            shape = PixelRounded8Shape,
            offsetY = 8.dp,
            color = Color.Black.copy(alpha = 0.4f)
        )
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawBehind {
            // Your pixel shape drawing with BlendMode.Clear corners
        }
) {
    // Menu content
}
```

## Notes

- The `cutColor` parameter is still in the API for backward compatibility but is no longer used for corner cutting
- You can still use `cutColor` for the speech bubble tail in `PixelBox`
- Always apply `pixelShadow()` BEFORE your shape drawing modifiers
- Shadows are drawn using the Shape objects, so they're pixel-perfect
