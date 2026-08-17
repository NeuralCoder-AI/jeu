package com.example

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Game States for Space Dodger
 */
enum class GameState {
    START,
    PLAYING,
    GAME_OVER
}

/**
 * Types of Power-ups
 */
enum class PowerUpType {
    SHIELD,        // Blue: Grants a protective shield
    SCORE_BONUS    // Yellow: Grants +50 points immediately
}

/**
 * Background star representation
 */
private data class Star(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float,
    val color: Color
)

/**
 * Asteroid obstacle representation
 */
private data class Asteroid(
    var x: Float,
    var y: Float,
    val radius: Float,
    val speed: Float,
    var rotation: Float,
    val rotationSpeed: Float,
    val shapeFactors: List<Float>, // Irregular polygon vertices radius multipliers
    val baseColor: Color,
    val craterColor: Color
)

/**
 * Power-up item representation
 */
private data class PowerUp(
    var x: Float,
    var y: Float,
    val radius: Float,
    val speed: Float,
    val type: PowerUpType,
    var pulsePhase: Float = 0f
)

/**
 * Visual particle for explosions and engine trails
 */
private data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val size: Float,
    val color: Color,
    var life: Float = 1f,
    val decay: Float = 0.03f
)

/**
 * Floating feedback text (+50, SHIELD!)
 */
private data class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Color,
    var life: Float = 1f
)

/**
 * Main Space Dodger Game Composable
 */
@Composable
fun SpaceDodgerGame(
    modifier: Modifier = Modifier
) {
    // Current Game State
    var gameState by remember { mutableStateOf(GameState.START) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(0) }
    var hasShield by remember { mutableStateOf(false) }

    // Screen dimensions in pixels (tracked dynamically)
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }

    // Spaceship position and physics
    var shipX by remember { mutableFloatStateOf(540f) }
    var shipY by remember { mutableFloatStateOf(1600f) }
    var shipVelocityX by remember { mutableFloatStateOf(0f) }
    var shipTilt by remember { mutableFloatStateOf(0f) }
    val shipRadius = 36f

    // Touch input state: -1 (left), 0 (none), 1 (right)
    var touchDirection by remember { mutableFloatStateOf(0f) }

    // Game Entities
    val stars = remember { mutableStateListOf<Star>() }
    val asteroids = remember { mutableStateListOf<Asteroid>() }
    val powerUps = remember { mutableStateListOf<PowerUp>() }
    val particles = remember { mutableStateListOf<Particle>() }
    val floatingTexts = remember { mutableStateListOf<FloatingText>() }

    // Spawn Timers & Difficulty Tracking
    var lastAsteroidSpawnTime by remember { mutableLongStateOf(0L) }
    var lastPowerUpSpawnTime by remember { mutableLongStateOf(0L) }
    var scoreAccumulator by remember { mutableFloatStateOf(0f) }
    var shieldHitAnimation by remember { mutableFloatStateOf(0f) }

    // UI Pulsing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "game_pulse")
    val titleGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "title_glow"
    )

    // Function to reset and start a new game session
    fun startNewGame() {
        shipX = screenWidth / 2f
        shipY = screenHeight * 0.82f
        shipVelocityX = 0f
        shipTilt = 0f
        touchDirection = 0f
        score = 0
        scoreAccumulator = 0f
        hasShield = false
        shieldHitAnimation = 0f
        asteroids.clear()
        powerUps.clear()
        particles.clear()
        floatingTexts.clear()
        lastAsteroidSpawnTime = System.currentTimeMillis()
        lastPowerUpSpawnTime = System.currentTimeMillis()
        gameState = GameState.PLAYING
    }

    // Function to trigger explosion particles
    fun spawnExplosion(x: Float, y: Float, count: Int, primaryColor: Color, secondaryColor: Color) {
        repeat(count) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 8f + 2f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    size = Random.nextFloat() * 7f + 3f,
                    color = if (Random.nextBoolean()) primaryColor else secondaryColor,
                    life = 1f,
                    decay = Random.nextFloat() * 0.035f + 0.015f
                )
            )
        }
    }

    // Initialize Stars once
    LaunchedEffect(Unit) {
        if (stars.isEmpty()) {
            repeat(90) {
                val depth = Random.nextFloat()
                stars.add(
                    Star(
                        x = Random.nextFloat() * 1400f,
                        y = Random.nextFloat() * 2500f,
                        size = depth * 3.5f + 1.2f,
                        speed = depth * 2.5f + 0.5f,
                        alpha = depth * 0.7f + 0.3f,
                        color = when {
                            depth > 0.8f -> Color(0xFF80D8FF)
                            depth > 0.5f -> Color(0xFFFFFFFF)
                            else -> Color(0xFFFFD54F)
                        }
                    )
                )
            }
        }
    }

    // High performance Game Loop running with withFrameNanos (~60+ FPS)
    LaunchedEffect(gameState) {
        if (gameState != GameState.PLAYING) return@LaunchedEffect

        var lastFrameNanos = 0L

        while (gameState == GameState.PLAYING) {
            withFrameNanos { currentFrameNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = currentFrameNanos
                    return@withFrameNanos
                }

                val deltaNanos = currentFrameNanos - lastFrameNanos
                lastFrameNanos = currentFrameNanos
                val dt = (deltaNanos / 1_000_000_000f).coerceIn(0.001f, 0.05f)

                // 1. Difficulty progression based on score
                val difficulty = (1f + (score / 100f) * 0.35f).coerceAtMost(3.2f)
                val baseAsteroidSpeed = (220f + score * 1.5f).coerceAtMost(650f)
                val spawnIntervalMs = (1400L / difficulty).toLong().coerceAtLeast(380L)

                // 2. Passive score increment over time
                scoreAccumulator += dt * 10f * difficulty
                if (scoreAccumulator >= 1f) {
                    val points = scoreAccumulator.toInt()
                    score += points
                    scoreAccumulator -= points
                    if (score > highScore) {
                        highScore = score
                    }
                }

                // 3. Spaceship steering and movement
                val targetSpeed = touchDirection * 700f
                shipVelocityX += (targetSpeed - shipVelocityX) * (15f * dt)
                shipX = (shipX + shipVelocityX * dt).coerceIn(shipRadius + 10f, screenWidth - shipRadius - 10f)
                shipY = screenHeight * 0.82f

                // Tilt effect based on velocity
                val targetTilt = (shipVelocityX / 700f) * 22f
                shipTilt += (targetTilt - shipTilt) * (12f * dt)

                // Engine thruster particles
                if (Random.nextFloat() < 0.8f) {
                    particles.add(
                        Particle(
                            x = shipX + (Random.nextFloat() - 0.5f) * 14f,
                            y = shipY + 28f,
                            vx = (Random.nextFloat() - 0.5f) * 40f,
                            vy = Random.nextFloat() * 120f + 160f,
                            size = Random.nextFloat() * 5f + 3f,
                            color = if (Random.nextBoolean()) Color(0xFFFFB300) else Color(0xFFFF3D00),
                            life = 1f,
                            decay = 0.06f
                        )
                    )
                }

                // 4. Update shield hit flash animation
                if (shieldHitAnimation > 0f) {
                    shieldHitAnimation = (shieldHitAnimation - dt * 3f).coerceAtLeast(0f)
                }

                // 5. Spawn Asteroids
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAsteroidSpawnTime > spawnIntervalMs) {
                    lastAsteroidSpawnTime = currentTime
                    val asteroidRadius = Random.nextFloat() * 22f + 20f
                    val spawnX = Random.nextFloat() * (screenWidth - asteroidRadius * 2f) + asteroidRadius
                    val shapeCount = 7
                    val factors = List(shapeCount) { Random.nextFloat() * 0.4f + 0.8f }
                    val speed = (baseAsteroidSpeed * (Random.nextFloat() * 0.4f + 0.8f))

                    asteroids.add(
                        Asteroid(
                            x = spawnX,
                            y = -asteroidRadius - 20f,
                            radius = asteroidRadius,
                            speed = speed,
                            rotation = Random.nextFloat() * 360f,
                            rotationSpeed = (Random.nextFloat() - 0.5f) * 120f,
                            shapeFactors = factors,
                            baseColor = if (Random.nextBoolean()) Color(0xFF795548) else Color(0xFF616161),
                            craterColor = Color(0xFF3E2723)
                        )
                    )
                }

                // 6. Spawn Power-ups (Shield or Bonus Points)
                if (currentTime - lastPowerUpSpawnTime > 6500L) {
                    lastPowerUpSpawnTime = currentTime
                    val powerUpType = if (!hasShield && Random.nextFloat() < 0.55f) {
                        PowerUpType.SHIELD
                    } else {
                        PowerUpType.SCORE_BONUS
                    }
                    val spawnX = Random.nextFloat() * (screenWidth - 100f) + 50f

                    powerUps.add(
                        PowerUp(
                            x = spawnX,
                            y = -40f,
                            radius = 24f,
                            speed = 180f,
                            type = powerUpType
                        )
                    )
                }

                // 7. Update Asteroids & check collisions
                val asteroidIterator = asteroids.iterator()
                while (asteroidIterator.hasNext()) {
                    val asteroid = asteroidIterator.next()
                    asteroid.y += asteroid.speed * dt
                    asteroid.rotation += asteroid.rotationSpeed * dt

                    // Distance-based collision with player spaceship
                    val dx = asteroid.x - shipX
                    val dy = asteroid.y - shipY
                    val dist = sqrt(dx * dx + dy * dy)
                    val collisionDistance = asteroid.radius + shipRadius - 6f

                    if (dist < collisionDistance) {
                        // Collision occurred!
                        if (hasShield) {
                            // Shield absorbs the asteroid!
                            hasShield = false
                            shieldHitAnimation = 1f
                            spawnExplosion(asteroid.x, asteroid.y, 22, Color(0xFF00E5FF), Color(0xFFFFFFFF))
                            floatingTexts.add(
                                FloatingText(
                                    x = shipX,
                                    y = shipY - 40f,
                                    text = "BOUCLIER BRISÉ !",
                                    color = Color(0xFF80D8FF)
                                )
                            )
                            asteroidIterator.remove()
                        } else {
                            // Ship destroyed -> Game Over!
                            spawnExplosion(shipX, shipY, 40, Color(0xFFFF5252), Color(0xFFFF9800))
                            gameState = GameState.GAME_OVER
                            return@withFrameNanos
                        }
                    } else if (asteroid.y > screenHeight + asteroid.radius + 60f) {
                        // Asteroid escaped the screen
                        asteroidIterator.remove()
                    }
                }

                // 8. Update Power-ups & check pickup
                val powerUpIterator = powerUps.iterator()
                while (powerUpIterator.hasNext()) {
                    val powerUp = powerUpIterator.next()
                    powerUp.y += powerUp.speed * dt
                    powerUp.pulsePhase += dt * 5f

                    // Distance-based pickup detection
                    val dx = powerUp.x - shipX
                    val dy = powerUp.y - shipY
                    val dist = sqrt(dx * dx + dy * dy)
                    val pickupDistance = powerUp.radius + shipRadius + 6f

                    if (dist < pickupDistance) {
                        when (powerUp.type) {
                            PowerUpType.SHIELD -> {
                                hasShield = true
                                spawnExplosion(powerUp.x, powerUp.y, 16, Color(0xFF00E5FF), Color(0xFF2979FF))
                                floatingTexts.add(
                                    FloatingText(
                                        x = shipX,
                                        y = shipY - 40f,
                                        text = "BOUCLIER ACTIF !",
                                        color = Color(0xFF00E5FF)
                                    )
                                )
                            }
                            PowerUpType.SCORE_BONUS -> {
                                score += 50
                                if (score > highScore) {
                                    highScore = score
                                }
                                spawnExplosion(powerUp.x, powerUp.y, 18, Color(0xFFFFD700), Color(0xFFFFEA00))
                                floatingTexts.add(
                                    FloatingText(
                                        x = shipX,
                                        y = shipY - 40f,
                                        text = "+50 POINTS !",
                                        color = Color(0xFFFFD700)
                                    )
                                )
                            }
                        }
                        powerUpIterator.remove()
                    } else if (powerUp.y > screenHeight + 60f) {
                        powerUpIterator.remove()
                    }
                }

                // 9. Update Particles
                val particleIterator = particles.iterator()
                while (particleIterator.hasNext()) {
                    val p = particleIterator.next()
                    p.x += p.vx * dt * 60f
                    p.y += p.vy * dt * 60f
                    p.life -= p.decay
                    if (p.life <= 0f) {
                        particleIterator.remove()
                    }
                }

                // 10. Update Floating texts
                val textIterator = floatingTexts.iterator()
                while (textIterator.hasNext()) {
                    val ft = textIterator.next()
                    ft.y -= dt * 45f
                    ft.life -= dt * 0.9f
                    if (ft.life <= 0f) {
                        textIterator.remove()
                    }
                }

                // 11. Update Parallax Stars
                for (star in stars) {
                    star.y += star.speed * (dt * 60f) * (1f + (difficulty - 1f) * 0.5f)
                    if (star.y > screenHeight + 20f) {
                        star.y = -10f
                        star.x = Random.nextFloat() * screenWidth
                    }
                }
            }
        }
    }

    // Main Canvas & Game Layout Container
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B19))
            .pointerInput(gameState) {
                if (gameState == GameState.PLAYING) {
                    // Tap gestures: Left half to move left, Right half to move right
                    detectTapGestures(
                        onPress = { offset ->
                            touchDirection = if (offset.x < size.width / 2f) -1f else 1f
                            tryAwaitRelease()
                            touchDirection = 0f
                        }
                    )
                }
            }
            .pointerInput(gameState) {
                if (gameState == GameState.PLAYING) {
                    // Drag gestures for fluid manual steering
                    detectDragGestures(
                        onDragStart = { offset ->
                            touchDirection = if (offset.x < size.width / 2f) -1f else 1f
                        },
                        onDragEnd = { touchDirection = 0f },
                        onDragCancel = { touchDirection = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            shipX = (shipX + dragAmount.x * 1.3f).coerceIn(shipRadius + 10f, size.width - shipRadius - 10f)
                            shipVelocityX = dragAmount.x * 15f
                        }
                    )
                }
            }
    ) {
        // Record container sizes
        val density = androidx.compose.ui.platform.LocalDensity.current
        LaunchedEffect(constraints) {
            screenWidth = constraints.maxWidth.toFloat()
            screenHeight = constraints.maxHeight.toFloat()
            if (shipX == 540f && screenWidth > 0f) {
                shipX = screenWidth / 2f
                shipY = screenHeight * 0.82f
            }
        }

        // --- 100% Vector Rendered Game Canvas ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Cosmic Deep Space Background Gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF04060E),
                        Color(0xFF0A1024),
                        Color(0xFF050814)
                    )
                ),
                size = Size(canvasWidth, canvasHeight)
            )

            // Subtle nebula background glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x181E88E5), Color.Transparent),
                    center = Offset(canvasWidth * 0.2f, canvasHeight * 0.3f),
                    radius = canvasWidth * 0.6f
                ),
                radius = canvasWidth * 0.6f,
                center = Offset(canvasWidth * 0.2f, canvasHeight * 0.3f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x147B1FA2), Color.Transparent),
                    center = Offset(canvasWidth * 0.8f, canvasHeight * 0.7f),
                    radius = canvasWidth * 0.5f
                ),
                radius = canvasWidth * 0.5f,
                center = Offset(canvasWidth * 0.8f, canvasHeight * 0.7f)
            )

            // 2. Stars Layer
            for (star in stars) {
                drawCircle(
                    color = star.color.copy(alpha = star.alpha),
                    radius = star.size,
                    center = Offset(star.x.coerceIn(0f, canvasWidth), star.y)
                )
            }

            // 3. Particles (Thrusters, Explosions, Sparks)
            for (p in particles) {
                drawCircle(
                    color = p.color.copy(alpha = p.life.coerceIn(0f, 1f)),
                    radius = p.size * p.life,
                    center = Offset(p.x, p.y)
                )
            }

            // 4. Asteroids
            for (asteroid in asteroids) {
                drawAsteroid(asteroid)
            }

            // 5. Power-ups (Shield & Points)
            for (powerUp in powerUps) {
                drawPowerUp(powerUp)
            }

            // 6. Spaceship
            if (gameState == GameState.PLAYING || gameState == GameState.START) {
                drawSpaceship(
                    x = shipX,
                    y = shipY,
                    tilt = shipTilt,
                    hasShield = hasShield,
                    shieldHitAnimation = shieldHitAnimation
                )
            }
        }

        // --- HUD OVERLAY (Only while PLAYING) ---
        if (gameState == GameState.PLAYING) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                // Top HUD Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x800D152B))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Score Display
                    Column {
                        Text(
                            text = "SCORE",
                            color = Color(0xFF90CAF9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "$score",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Shield Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasShield) Color(0xFF0D47A1) else Color(0x33212121),
                        modifier = Modifier.shadow(if (hasShield) 8.dp else 0.dp, shape = RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Statut Bouclier",
                                tint = if (hasShield) Color(0xFF00E5FF) else Color(0xFF757575),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasShield) "BOUCLIER ACTIF" else "SANS BOUCLIER",
                                color = if (hasShield) Color(0xFF00E5FF) else Color(0xFF9E9E9E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // High Score Display
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Meilleur Score",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RECORD",
                                color = Color(0xFFFFD700),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "$highScore",
                            color = Color(0xFFFFECB3),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Dynamic Floating Text Indicators
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (ft in floatingTexts) {
                        Text(
                            text = ft.text,
                            color = ft.color.copy(alpha = ft.life.coerceIn(0f, 1f)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = (ft.y * 0.15f).coerceAtLeast(20f).dp)
                        )
                    }
                }

                // Semi-transparent Touch Guidance Hints at Bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "◀ TAP GAUCHE",
                        color = Color(0x55FFFFFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Text(
                        text = "TAP DROITE ▶",
                        color = Color(0x55FFFFFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }
        }

        // --- START SCREEN OVERLAY ---
        if (gameState == GameState.START) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC050814)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 450.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Futuristic Glowing Logo / Title
                    Text(
                        text = "SPACE",
                        color = Color(0xFF00E5FF),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.scale(titleGlowScale)
                    )
                    Text(
                        text = "DODGER",
                        color = Color(0xFF00E676),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 8.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.scale(titleGlowScale)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Survivez dans l'espace infini !",
                        color = Color(0xFFB0BEC5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    // Instructions Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A36)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(12.dp, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "COMMENT JOUER",
                                color = Color(0xFF90CAF9),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0x3300E5FF),
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = "👈👉", fontSize = 16.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Touchez la gauche ou la droite de l'écran pour diriger le vaisseau.",
                                    color = Color(0xFFECEFF1),
                                    fontSize = 13.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0x3300E5FF),
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Bouclier",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Bouclier (Bleu) : Protège d'un impact contre un astéroïde.",
                                    color = Color(0xFFECEFF1),
                                    fontSize = 13.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0x33FFD700),
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Bonus",
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Bonus Or (Jaune) : +50 points instantanés.",
                                    color = Color(0xFFECEFF1),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // Play Button
                    Button(
                        onClick = { startNewGame() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color(0xFF041E0F)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF00E676))
                            .testTag("play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Jouer",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "JOUER",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }

        // --- GAME OVER SCREEN OVERLAY ---
        if (gameState == GameState.GAME_OVER) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD0A050B)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "GAME OVER",
                        color = Color(0xFFFF5252),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Score Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1124)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "SCORE FINAL",
                                color = Color(0xFFB0BEC5),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "$score",
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x33FFD700))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = "Meilleur Score",
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "MEILLEUR RECORD",
                                        color = Color(0xFFFFD700),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "$highScore",
                                    color = Color(0xFFFFECB3),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Replay Button
                    Button(
                        onClick = { startNewGame() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF002233)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF00E5FF))
                            .testTag("replay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rejouer",
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "REJOUER",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draw Vector Spaceship on Canvas
 */
private fun DrawScope.drawSpaceship(
    x: Float,
    y: Float,
    tilt: Float,
    hasShield: Boolean,
    shieldHitAnimation: Float
) {
    rotate(degrees = tilt, pivot = Offset(x, y)) {
        // Shield Glow Ring (if active or just absorbed an impact)
        if (hasShield || shieldHitAnimation > 0f) {
            val shieldAlpha = if (hasShield) 0.55f else shieldHitAnimation
            val shieldGlowRadius = 52f + (if (hasShield) 0f else (1f - shieldHitAnimation) * 15f)

            // Outer Shield Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x3300E5FF).copy(alpha = shieldAlpha * 0.5f),
                        Color(0x6600E5FF).copy(alpha = shieldAlpha),
                        Color.Transparent
                    ),
                    center = Offset(x, y),
                    radius = shieldGlowRadius
                ),
                radius = shieldGlowRadius,
                center = Offset(x, y)
            )

            // Shield Perimeter Energy Arc
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = shieldAlpha),
                radius = 46f,
                center = Offset(x, y),
                style = Stroke(width = 3.5f)
            )
        }

        // Thruster Engine Flame (dynamic animated triangle)
        val flamePath = Path().apply {
            moveTo(x - 10f, y + 20f)
            lineTo(x, y + 42f)
            lineTo(x + 10f, y + 20f)
            close()
        }
        drawPath(
            path = flamePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFEA00), Color(0xFFFF3D00), Color.Transparent),
                startY = y + 20f,
                endY = y + 44f
            )
        )

        // Spaceship Main Wings (Cyan/Emerald Geometric Polygon)
        val wingPath = Path().apply {
            moveTo(x, y - 36f)           // Nose tip
            lineTo(x - 30f, y + 24f)     // Left wing tip
            lineTo(x - 12f, y + 16f)     // Left wing inner notch
            lineTo(x, y + 22f)           // Fuselage base
            lineTo(x + 12f, y + 16f)     // Right wing inner notch
            lineTo(x + 30f, y + 24f)     // Right wing tip
            close()
        }
        drawPath(
            path = wingPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF00E676), Color(0xFF00B0FF)),
                startY = y - 36f,
                endY = y + 24f
            )
        )

        // Wing Border Accent Lines
        drawPath(
            path = wingPath,
            color = Color(0xFFE0F2F1),
            style = Stroke(width = 1.8f)
        )

        // Spaceship Center Fuselage & Cockpit Canopy
        val cockpitPath = Path().apply {
            moveTo(x, y - 28f)
            lineTo(x - 8f, y + 4f)
            lineTo(x, y + 12f)
            lineTo(x + 8f, y + 4f)
            close()
        }
        drawPath(
            path = cockpitPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF80D8FF)),
                startY = y - 28f,
                endY = y + 12f
            )
        )

        // Wing Cannon Energy Blasters
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 3f,
            center = Offset(x - 26f, y + 18f)
        )
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 3f,
            center = Offset(x + 26f, y + 18f)
        )
    }
}

/**
 * Draw Vector Asteroid on Canvas
 */
private fun DrawScope.drawAsteroid(asteroid: Asteroid) {
    rotate(degrees = asteroid.rotation, pivot = Offset(asteroid.x, asteroid.y)) {
        val path = Path()
        val numVertices = asteroid.shapeFactors.size
        val angleStep = (2f * Math.PI / numVertices).toFloat()

        for (i in 0 until numVertices) {
            val angle = i * angleStep
            val r = asteroid.radius * asteroid.shapeFactors[i]
            val vx = asteroid.x + cos(angle) * r
            val vy = asteroid.y + sin(angle) * r

            if (i == 0) {
                path.moveTo(vx, vy)
            } else {
                path.lineTo(vx, vy)
            }
        }
        path.close()

        // Asteroid Body
        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(
                    asteroid.baseColor,
                    Color(0xFF3E2723)
                ),
                center = Offset(asteroid.x - asteroid.radius * 0.3f, asteroid.y - asteroid.radius * 0.3f),
                radius = asteroid.radius * 1.3f
            )
        )

        // Rocky Outline
        drawPath(
            path = path,
            color = Color(0xFF2E1C14),
            style = Stroke(width = 2f)
        )

        // Craters on Asteroid Surface
        drawCircle(
            color = asteroid.craterColor,
            radius = asteroid.radius * 0.22f,
            center = Offset(asteroid.x - asteroid.radius * 0.3f, asteroid.y - asteroid.radius * 0.2f)
        )
        drawCircle(
            color = asteroid.craterColor,
            radius = asteroid.radius * 0.16f,
            center = Offset(asteroid.x + asteroid.radius * 0.25f, asteroid.y + asteroid.radius * 0.25f)
        )
        drawCircle(
            color = asteroid.craterColor,
            radius = asteroid.radius * 0.12f,
            center = Offset(asteroid.x + asteroid.radius * 0.1f, asteroid.y - asteroid.radius * 0.35f)
        )
    }
}

/**
 * Draw Vector Power-Up (Shield or Bonus Points) on Canvas
 */
private fun DrawScope.drawPowerUp(powerUp: PowerUp) {
    val pulse = (sin(powerUp.pulsePhase) * 4f).toFloat()
    val r = powerUp.radius + pulse

    when (powerUp.type) {
        PowerUpType.SHIELD -> {
            // Blue Shield Power-up
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x6600E5FF),
                        Color(0x2200E5FF),
                        Color.Transparent
                    ),
                    center = Offset(powerUp.x, powerUp.y),
                    radius = r + 12f
                ),
                radius = r + 12f,
                center = Offset(powerUp.x, powerUp.y)
            )
            drawCircle(
                color = Color(0xFF00B0FF),
                radius = r,
                center = Offset(powerUp.x, powerUp.y)
            )
            drawCircle(
                color = Color(0xFFFFFFFF),
                radius = r,
                center = Offset(powerUp.x, powerUp.y),
                style = Stroke(width = 2.5f)
            )

            // Inner Shield Vector Shape
            val iconPath = Path().apply {
                moveTo(powerUp.x, powerUp.y - 10f)
                lineTo(powerUp.x - 8f, powerUp.y - 4f)
                lineTo(powerUp.x - 8f, powerUp.y + 4f)
                lineTo(powerUp.x, powerUp.y + 10f)
                lineTo(powerUp.x + 8f, powerUp.y + 4f)
                lineTo(powerUp.x + 8f, powerUp.y - 4f)
                close()
            }
            drawPath(
                path = iconPath,
                color = Color.White,
                style = Fill
            )
        }

        PowerUpType.SCORE_BONUS -> {
            // Yellow / Gold Score Bonus
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x66FFD700),
                        Color(0x22FFD700),
                        Color.Transparent
                    ),
                    center = Offset(powerUp.x, powerUp.y),
                    radius = r + 12f
                ),
                radius = r + 12f,
                center = Offset(powerUp.x, powerUp.y)
            )
            drawCircle(
                color = Color(0xFFFFD700),
                radius = r,
                center = Offset(powerUp.x, powerUp.y)
            )
            drawCircle(
                color = Color(0xFFFFF9C4),
                radius = r,
                center = Offset(powerUp.x, powerUp.y),
                style = Stroke(width = 2.5f)
            )

            // Inner Star Shape
            val starPath = Path()
            val points = 5
            val outerRadius = 10f
            val innerRadius = 4.5f
            val step = (Math.PI / points).toFloat()

            for (i in 0 until (points * 2)) {
                val currRadius = if (i % 2 == 0) outerRadius else innerRadius
                val angle = (i * step - Math.PI / 2f).toFloat()
                val px = powerUp.x + cos(angle) * currRadius
                val py = powerUp.y + sin(angle) * currRadius
                if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
            }
            starPath.close()

            drawPath(
                path = starPath,
                color = Color(0xFF5D4037),
                style = Fill
            )
        }
    }
}
