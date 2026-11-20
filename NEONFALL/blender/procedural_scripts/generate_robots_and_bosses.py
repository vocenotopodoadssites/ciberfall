"""
NEONFALL: RISE OF MACHINES - Blender Procedural Robots & Bosses Generator (bpy)
Generates modular base meshes for OMEGA-01, SERAPH-X, TITAN ZERO, and 8 robot classes.
"""

import bpy
import os

def create_boss_omega01():
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, 0, 1.4))
    torso = bpy.context.active_object
    torso.name = "OMEGA_01_Chassis"
    torso.scale = (2.4, 1.6, 2.2)

    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2, radius=0.65, location=(0, -0.9, 1.45))
    reactor = bpy.context.active_object
    reactor.name = "OMEGA_01_ReactorCore"
    reactor.parent = torso
    return torso

if __name__ == "__main__":
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    boss = create_boss_omega01()
    out_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../exports/glb/bosses"))
    os.makedirs(out_dir, exist_ok=True)
    bpy.ops.export_scene.gltf(filepath=os.path.join(out_dir, "omega_01.glb"), export_format='GLB')
    print("Exported OMEGA-01 boss prototype GLB:", out_dir)
