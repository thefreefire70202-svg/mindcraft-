package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmethystPurple
import com.example.ui.theme.DiamondBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RedstoneRed
import com.example.ui.theme.RobotoMonoFamily
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BotBrainScreen(
    simulationStep: Int,
    onAdvanceSimulation: () -> Unit,
    onResetSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "MINDCRAFT ARCHITECTURE EXPLORER",
                style = MaterialTheme.typography.titleLarge,
                color = DiamondBlue
            )
            Text(
                text = "Discover how Mindcraft transforms raw Minecraft voxel packets into autonomous AI agency and goal-driven gameplay.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        // Interactive Simulator Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("interactive_brain_simulator_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, DiamondBlue)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INTERACTIVE COGNITIVE PIPELINE",
                            style = MaterialTheme.typography.titleMedium,
                            color = DiamondBlue
                        )

                        Text(
                            text = "Phase ${simulationStep + 1} of 4",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldYellow
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Step indicator dots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PipelineStepBadge("1. Perception", isActive = simulationStep == 0, isDone = simulationStep > 0)
                        PipelineStepBadge("2. Prompting", isActive = simulationStep == 1, isDone = simulationStep > 1)
                        PipelineStepBadge("3. LLM Reason", isActive = simulationStep == 2, isDone = simulationStep > 2)
                        PipelineStepBadge("4. Action", isActive = simulationStep == 3, isDone = false)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Content Box
                    AnimatedContent(
                        targetState = simulationStep,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "simulator_content"
                    ) { step ->
                        when (step) {
                            0 -> SimulationStepCard(
                                title = "Stage 1: Minecraft World Perception",
                                description = "Mineflayer bot client parses server chunk packets, entity vectors, and player chat events.",
                                payloadSnippet = """
                                {
                                  "bot_position": [128.5, 64.0, -256.2],
                                  "health": 18, "food": 16,
                                  "nearby_blocks": ["oak_log x6", "iron_ore x3", "tall_grass"],
                                  "nearby_entities": [{"type": "zombie", "distance": 7.4}, {"type": "sheep", "distance": 4.1}],
                                  "player_input": "Andy, chop some trees and keep an eye on that zombie!"
                                }
                                """.trimIndent(),
                                accentColor = EmeraldGreen
                            )
                            1 -> SimulationStepCard(
                                title = "Stage 2: Mindcraft Prompt Synthesis",
                                description = "Perception is compiled into a concise structured prompt with Andy's persona and available action APIs.",
                                payloadSnippet = """
                                System: You are Andy the Mindcraft Agent. 
                                Goal: "Chop oak trees and guard against zombie."
                                Context: Health 18/20, Wooden Axe equipped, Zombie 7.4m away.
                                Instructions: Formulate a step-by-step strategy. Output thoughts, then output !commands.
                                """.trimIndent(),
                                accentColor = DiamondBlue
                            )
                            2 -> SimulationStepCard(
                                title = "Stage 3: LLM Cognitive Planning",
                                description = "The selected model (Cloud API or Local Edge AI) reasons about threat safety, equips tools, and generates action primitives.",
                                payloadSnippet = """
                                [AI THOUGHT]:
                                "1. The zombie is 7.4m away - within sprint range.
                                 2. Best course: equip iron sword, eliminate zombie first to ensure safety.
                                 3. Then switch to iron axe and harvest 6 oak logs."
                                
                                [GENERATED COMMANDS]:
                                !equip(iron_sword)
                                !defendFrom(zombie)
                                !equip(iron_axe)
                                !mine(oak_log, 6)
                                !chat("Engaging zombie before harvesting wood!")
                                """.trimIndent(),
                                accentColor = AmethystPurple
                            )
                            3 -> SimulationStepCard(
                                title = "Stage 4: Mineflayer Action Dispatch & Self-Healing",
                                description = "Commands are translated into Mineflayer pathfinder A* coordinates and action callbacks with error recovery.",
                                payloadSnippet = """
                                [MINEFLAYER DISPATCH]:
                                -> Action: bot.equip(bot.inventory.findInventoryItem('iron_sword'))
                                -> Pathfinding: A* navigation to entity zombie [SUCCESS]
                                -> Combat: bot.attack(zombie) [ZOMBIE ELIMINATED]
                                -> Task: pathfindToBlock(nearest oak_log)
                                -> Self-Healing Watchdog: Active (0 exceptions recorded)
                                """.trimIndent(),
                                accentColor = GoldYellow
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onAdvanceSimulation,
                            modifier = Modifier
                                .weight(2f)
                                .height(44.dp)
                                .testTag("advance_simulation_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = DiamondBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (simulationStep < 3) "NEXT PIPELINE STEP" else "CYCLE REASONING", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }

                        OutlinedButton(
                            onClick = onResetSimulation,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TextMuted)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RESET", color = TextMuted)
                        }
                    }
                }
            }
        }

        // Structural Explanation Cards
        item {
            Text(
                text = "HOW MINDCRAFT WORKS UNDER THE HOOD",
                style = MaterialTheme.typography.titleMedium,
                color = DiamondBlue
            )
        }

        item {
            ConceptCard(
                icon = "👁️",
                title = "1. High-Level Textual Perception",
                description = "Unlike legacy reinforcement learning agents that require raw pixels and low-level keypresses, Mindcraft leverages Mineflayer to transform blocks, inventories, and coordinates into semantic text representations that language models intuitively understand."
            )
        }

        item {
            ConceptCard(
                icon = "🧠",
                title = "2. Dynamic Tool Calling & Code Primitives",
                description = "Mindcraft instructs the LLM with custom syntax primitives: !goTo, !mine, !craft, !defendFrom, !eat, !chat. The model plans sequences of actions rather than predicting individual motor frames, granting the bot strategic autonomy."
            )
        }

        item {
            ConceptCard(
                icon = "🛡️",
                title = "3. Autonomous Self-Correction Loop",
                description = "When a tool breaks, a path is blocked by lava, or an action times out, the error is caught by the watchdog and reinjected into the model's conversation history. The LLM automatically deduces what went wrong and chooses an alternative solution."
            )
        }

        item {
            ConceptCard(
                icon = "⚡",
                title = "4. Hybrid Cloud & On-Device Local AI",
                description = "Mindcraft Bot Host supports running both zero-latency cloud APIs (Gemini, Claude, OpenAI, Groq) and ultra-compact on-device GGUF edge models (SmolLM2 360M) downloaded right inside this application."
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun PipelineStepBadge(label: String, isActive: Boolean, isDone: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = when {
            isActive -> DiamondBlue.copy(alpha = 0.2f)
            isDone -> EmeraldGreen.copy(alpha = 0.15f)
            else -> SurfaceElevated
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                isActive -> DiamondBlue
                isDone -> EmeraldGreen
                else -> SurfaceBorder
            }
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = when {
                isActive -> DiamondBlue
                isDone -> EmeraldGreen
                else -> TextMuted
            }
        )
    }
}

@Composable
fun SimulationStepCard(
    title: String,
    description: String,
    payloadSnippet: String,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(10.dp))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = accentColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalBackground, RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Text(
                text = payloadSnippet,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = RobotoMonoFamily, fontSize = 10.5.sp),
                color = TextPrimary
            )
        }
    }
}

@Composable
fun ConceptCard(icon: String, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = DiamondBlue)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
