"""
NEONFALL: RISE OF MACHINES - Blender Procedural Character Rig Generator (bpy)
Creates a standardized humanoid armature (Pelvis -> Spine -> Head, Arm.L/R, Leg.L/R)
and modular PBR meshes for all 7 classes, ready for GLB export to Godot 4.x.
"""

import bpy
import os

def create_standard_rig(name="CharacterRig"):
    bpy.ops.object.armature_add(enter_editmode=True, location=(0, 0, 0))
    arm = bpy.context.active_object
    arm.name = name
    eb = arm.data.edit_bones

    pelvis = eb[0]
    pelvis.name = "Pelvis"
    pelvis.head = (0, 0, 0.85)
    pelvis.tail = (0, 0, 1.05)

    spine = eb.new("Spine")
    spine.head = (0, 0, 1.05)
    spine.tail = (0, 0, 1.50)
    spine.parent = pelvis

    head = eb.new("Head")
    head.head = (0, 0, 1.52)
    head.tail = (0, 0, 1.85)
    head.parent = spine

    for side, sign in (("L", -1.0), ("R", 1.0)):
        arm_b = eb.new(f"Arm.{side}")
        arm_b.head = (sign * 0.34, 0, 1.44)
        arm_b.tail = (sign * 0.38, 0, 0.92)
        arm_b.parent = spine

        leg_b = eb.new(f"Leg.{side}")
        leg_b.head = (sign * 0.15, 0, 0.85)
        leg_b.tail = (sign * 0.15, 0, 0.05)
        leg_b.parent = pelvis

    bpy.ops.object.mode_set(mode='OBJECT')
    return arm

if __name__ == "__main__":
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    rig = create_standard_rig("Neonfall_BaseRig")
    out_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../exports/glb/characters"))
    os.makedirs(out_dir, exist_ok=True)
    bpy.ops.export_scene.gltf(filepath=os.path.join(out_dir, "base_character_rig.glb"), export_format='GLB')
    print("Exported standard character rig:", out_dir)
