extends CharacterBody3D
class_name PlayerController

## Modular third-person player controller for NEONFALL (Godot 4.3).
## VisualRoot/CharacterModel can be swapped for any Blender GLB rig without touching gameplay logic.

@export var class_id: String = "berserker"
@export var move_speed: float = 6.4
@export var sprint_multiplier: float = 1.25
@export var dodge_speed: float = 16.5
@export var dodge_duration: float = 0.24
@export var dodge_cooldown: float = 2.6

@onready var visual_root: Node3D = $VisualRoot
@onready var camera_pivot: Node3D = $CameraPivot
@onready var attack_system: Node = $AttackSystem
@onready var health_system: Node = $HealthSystem

var _dodge_timer: float = 0.0
var _dodge_cd_timer: float = 0.0
var _dodge_dir: Vector3 = Vector3.FORWARD

func _ready() -> void:
	add_to_group("player")
	_apply_class_data()

func _apply_class_data() -> void:
	var char_data: Dictionary = DataLoader.get_character(class_id)
	if char_data.is_empty():
		return
	var stats: Dictionary = char_data.get("stats", {})
	move_speed = float(stats.get("moveSpeed", 6.4))
	if health_system and health_system.has_method("configure"):
		health_system.configure(float(stats.get("maxHp", 120.0)), float(stats.get("maxShield", 40.0)), float(stats.get("armor", 4.0)))
	if attack_system and attack_system.has_method("configure"):
		attack_system.configure(char_data)

func _physics_process(delta: float) -> void:
	if _dodge_cd_timer > 0.0:
		_dodge_cd_timer = max(0.0, _dodge_cd_timer - delta)

	if Input.is_action_just_pressed("dodge") and _dodge_cd_timer <= 0.0 and _dodge_timer <= 0.0:
		_start_dodge()

	if Input.is_action_just_pressed("ability") and attack_system and attack_system.has_method("trigger_active_ability"):
		attack_system.trigger_active_ability()

	var input_vec := Input.get_vector("move_left", "move_right", "move_forward", "move_backward")
	var cam_basis := camera_pivot.global_transform.basis
	var cam_fwd := -cam_basis.z
	cam_fwd.y = 0.0
	cam_fwd = cam_fwd.normalized()
	var cam_right := cam_basis.x
	cam_right.y = 0.0
	cam_right = cam_right.normalized()

	var move_dir := (cam_right * input_vec.x - cam_fwd * input_vec.y).normalized()

	if _dodge_timer > 0.0:
		_dodge_timer -= delta
		velocity.x = _dodge_dir.x * dodge_speed
		velocity.z = _dodge_dir.z * dodge_speed
	elif move_dir.length() > 0.05:
		var spd := move_speed * (sprint_multiplier if Input.is_action_pressed("sprint") else 1.0)
		velocity.x = move_dir.x * spd
		velocity.z = move_dir.z * spd
		var target_angle := atan2(move_dir.x, move_dir.z)
		visual_root.rotation.y = lerp_angle(visual_root.rotation.y, target_angle, delta * 12.0)
	else:
		velocity.x = move_toward(velocity.x, 0.0, move_speed * 8.0 * delta)
		velocity.z = move_toward(velocity.z, 0.0, move_speed * 8.0 * delta)

	if not is_on_floor():
		velocity.y -= 24.0 * delta
	else:
		velocity.y = 0.0

	move_and_slide()

func _start_dodge() -> void:
	var input_vec := Input.get_vector("move_left", "move_right", "move_forward", "move_backward")
	if input_vec.length() > 0.1:
		var cam_basis := camera_pivot.global_transform.basis
		var fwd := -Vector3(cam_basis.z.x, 0.0, cam_basis.z.z).normalized()
		var right := Vector3(cam_basis.x.x, 0.0, cam_basis.x.z).normalized()
		_dodge_dir = (right * input_vec.x - fwd * input_vec.y).normalized()
	else:
		_dodge_dir = Vector3(sin(visual_root.rotation.y), 0.0, cos(visual_root.rotation.y))
	_dodge_timer = dodge_duration
	_dodge_cd_timer = dodge_cooldown

func swap_character_model(glb_scene: PackedScene) -> void:
	for child in visual_root.get_children():
		child.queue_free()
	var instance := glb_scene.instantiate()
	visual_root.add_child(instance)
