# NEONFALL: RISE OF MACHINES

Roguelite de ação e sobrevivência 3D em terceira pessoa ambientado em uma cidade cyberpunk pós-apocalíptica dominada por máquinas.

## Estrutura Entregue

1. **Aplicativo Android Jogável 3D (Kotlin + Jetpack Compose + OpenGL ES 3.0)**
   - Câmera em terceira pessoa real com `SpringArm` anti-colisão em prédios/contêineres, rotação por toque e ajuste de distância.
   - 7 classes jogáveis (`BERSERKER`, `ENGENHEIRO`, `ATIRADOR`, `TECNOMAGO`, `HACKER`, `GUARDIÃO`, `CAÇADOR`) e 5 skins por classe (`Padrão`, `Urbana`, `Militar`, `Neon`, `Lendária`) com pré-visualização 3D interativa.
   - Mundo cyberpunk modular com arranha-céus, edifícios industriais, contêineres, carros destruídos, barricadas, letreiros holográficos, asfalto molhado com poças neon, chuva 3D e neblina.
   - 8 tipos de robôs inimigos + 3 chefes gigantes (`OMEGA-01`, `SERAPH-X`, `TITAN ZERO`), combate com ataque automático/manual, esquiva, habilidades ativas, drones, torres, minas e 30 cartas de evolução.
   - Persistência local via Jetpack Preferences DataStore.

2. **Arquivos Portáveis Godot 4.3 + Blender (`bpy`) + JSON (`NEONFALL/`)**
   - `NEONFALL/godot/`: `project.godot`, cenas `.tscn` e scripts GDScript (`player_controller.gd`, `third_person_camera.gd`, `combat_system.gd`, `enemy_ai.gd`, `data_loader.gd`, `save_system.gd`, `game_manager.gd`).
   - `NEONFALL/blender/procedural_scripts/`: scripts Python `bpy` para gerar cidade modular, rigs padronizados e chefes, exportando `.glb` para o Godot.
   - `NEONFALL/data/`: arquivos JSON canônicos (`characters.json`, `enemies.json`, `abilities.json`, `cards.json`, `skins.json`).

## Como executar no Godot e Blender

```bash
# Gerar modelos GLB modulares no Blender 4.x
blender --background --python NEONFALL/blender/procedural_scripts/generate_cyberpunk_city.py
blender --background --python NEONFALL/blender/procedural_scripts/generate_characters.py
blender --background --python NEONFALL/blender/procedural_scripts/generate_robots_and_bosses.py
```

Depois, abra `NEONFALL/godot/project.godot` no **Godot 4.3+** e execute a cena principal (`main_menu.tscn`).
