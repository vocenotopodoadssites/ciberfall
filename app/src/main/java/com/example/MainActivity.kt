package com.example

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.Routes
import com.example.ui.screens.CharacterSelectScreen
import com.example.ui.screens.DeckBuilderScreen
import com.example.ui.screens.GameplayScreen
import com.example.ui.screens.MainMenuScreen
import com.example.ui.screens.MissionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShopInventoryScreen
import com.example.ui.screens.UpgradesScreen
import com.example.ui.theme.NeonfallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeonfallTheme {
                NeonfallApp()
            }
        }
    }
}

@Composable
fun NeonfallApp(vm: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val save by vm.saveState.collectAsStateWithLifecycle()
    val activeSim by vm.activeSimulation.collectAsStateWithLifecycle()
    val hud by vm.hudState.collectAsStateWithLifecycle()
    val view = LocalView.current

    val triggerHaptic: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    NavHost(navController = navController, startDestination = Routes.MAIN_MENU) {
        composable(Routes.MAIN_MENU) {
            val cls = vm.selectedClass(save)
            val skin = vm.equippedSkin(save, cls.id)
            MainMenuScreen(
                save = save,
                accountLevel = vm.playerAccountLevel(save),
                selectedClass = cls,
                equippedSkin = skin,
                onPlay = {
                    vm.startNewRun(triggerHaptic)
                    navController.navigate(Routes.GAMEPLAY)
                },
                onNavigateCharacters = { navController.navigate(Routes.CHARACTER_SELECT) },
                onNavigateDeck = { navController.navigate(Routes.DECK_BUILDER) },
                onNavigateShop = { navController.navigate(Routes.SHOP) },
                onNavigateUpgrades = { navController.navigate(Routes.UPGRADES) },
                onNavigateMissions = { navController.navigate(Routes.MISSIONS) },
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.CHARACTER_SELECT) {
            CharacterSelectScreen(
                classes = vm.catalog.classes,
                skins = vm.catalog.skins,
                abilities = vm.catalog.abilities,
                save = save,
                onSelectClass = vm::selectClass,
                onUnlockClass = vm::unlockClass,
                onEquipSkin = vm::buyOrEquipSkin,
                onPlay = {
                    vm.startNewRun(triggerHaptic)
                    navController.navigate(Routes.GAMEPLAY)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DECK_BUILDER) {
            DeckBuilderScreen(
                cards = vm.catalog.cards,
                save = save,
                onToggleCard = vm::toggleDeckCard,
                onApplyPreset = vm::applyDeckPreset,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SHOP) {
            ShopInventoryScreen(
                classes = vm.catalog.classes,
                skins = vm.catalog.skins,
                save = save,
                onUnlockClass = vm::unlockClass,
                onBuyOrEquipSkin = vm::buyOrEquipSkin,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.UPGRADES) {
            UpgradesScreen(
                upgrades = vm.permanentUpgrades,
                save = save,
                onPurchase = vm::purchaseUpgrade,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MISSIONS) {
            MissionsScreen(
                achievements = vm.achievements,
                save = save,
                metricValue = vm::achievementMetricValue,
                onClaim = vm::claimAchievement,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                save = save,
                onUpdateQuality = { vm.updateSettings(quality = it) },
                onUpdateCameraDist = { vm.updateSettings(cameraDistance = it) },
                onToggleRain = { vm.updateSettings(rainEnabled = it) },
                onToggleAutoAttack = { vm.updateSettings(autoAttack = it) },
                onToggleHaptics = { vm.updateSettings(haptics = it) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.GAMEPLAY) {
            val sim = activeSim
            if (sim != null) {
                GameplayScreen(
                    sim = sim,
                    hud = hud,
                    save = save,
                    onHudTick = vm::onHudSnapshot,
                    onTogglePause = vm::togglePause,
                    onSelectCard = vm::chooseCard,
                    onUpdateQuality = { vm.updateSettings(quality = it) },
                    onUpdateCameraDist = { vm.updateSettings(cameraDistance = it) },
                    onPlayAgain = { vm.startNewRun(triggerHaptic) },
                    onExitToMenu = {
                        vm.abandonRun()
                        navController.popBackStack(Routes.MAIN_MENU, inclusive = false)
                    }
                )
            }
        }
    }
}
