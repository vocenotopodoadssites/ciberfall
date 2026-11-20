"""
NEONFALL: RISE OF MACHINES - Blender Procedural Cyberpunk City Generator (bpy)
Generates modular, independent 3D objects (skyscrapers, industrial buildings,
shipping containers, wrecked cars, streetlamps, holographic signs) with PBR materials.
Run inside Blender:
    blender --background --python generate_cyberpunk_city.py
"""

import bpy
import os

def clear_scene():
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)

def make_pbr_material(name, base_color, metallic=0.5, roughness=0.4, emission_color=None, emission_strength=0.0):
    mat = bpy.data.materials.new(name=name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (*base_color, 1.0)
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = roughness
    if emission_color and "Emission Color" in bsdf.inputs:
        bsdf.inputs["Emission Color"].default_value = (*emission_color, 1.0)
        bsdf.inputs["Emission Strength"].default_value = emission_strength
    return mat

def create_modular_skyscraper(name, width=10.0, height=28.0, depth=10.0, neon_color=(0.0, 0.94, 1.0)):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, 0, height * 0.5))
    bld = bpy.context.active_object
    bld.name = name
    bld.scale = (width, depth, height)
    bld.data.materials.append(make_pbr_material(f"{name}_Mat", (0.09, 0.11, 0.17), metallic=0.4, roughness=0.55))

    # Add glowing neon trim strips as child objects
    neon_mat = make_pbr_material(f"{name}_Neon", neon_color, metallic=0.1, roughness=0.2, emission_color=neon_color, emission_strength=4.5)
    for z_frac in (0.45, 0.78):
        bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, 0, height * z_frac))
        strip = bpy.context.active_object
        strip.name = f"{name}_Trim_{z_frac}"
        strip.scale = (width + 0.25, depth + 0.25, 0.45)
        strip.data.materials.append(neon_mat)
        strip.parent = bld
    return bld

def create_shipping_container(name, color=(0.18, 0.28, 0.34)):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, 0, 1.3))
    obj = bpy.context.active_object
    obj.name = name
    obj.scale = (4.6, 2.2, 2.6)
    obj.data.materials.append(make_pbr_material(f"{name}_RustPBR", color, metallic=0.75, roughness=0.58))
    return obj

def export_asset(obj, out_dir, filename):
    os.makedirs(out_dir, exist_ok=True)
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    for child in obj.children:
        child.select_set(True)
    bpy.ops.export_scene.gltf(
        filepath=os.path.join(out_dir, filename),
        use_selection=True,
        export_format='GLB'
    )

if __name__ == "__main__":
    clear_scene()
    out_glb = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../exports/glb/environment"))
    b1 = create_modular_skyscraper("Skyscraper_Cyan", neon_color=(0.0, 0.94, 1.0))
    export_asset(b1, out_glb, "skyscraper_cyan.glb")
    clear_scene()
    c1 = create_shipping_container("Container_Industrial")
    export_asset(c1, out_glb, "container_industrial.glb")
    print("NEONFALL modular environment assets exported to:", out_glb)
