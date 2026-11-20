extends Node
class_name EnemyAI

## State-based AI for NEONFALL robots and giant bosses.
## Visual mesh is decoupled under $VisualRoot so Blender GLBs can replace placeholder meshes cleanly.

enum AIState { IDLE, CHASE, ATTACK, STUNNED }

@export var enemy_id: String = "soldado_robotico"
@export var is_boss: bool = false

var state: AIState = AIState.CHASE
var hp: float = 75.0
var max_hp: float = 75.0
var speed: float = 3.4
var damage: float = 14.0
var attack_range: float = 9.0
var _attack_cd: float = 0.0

@onready var body: CharacterBody3D = get_parent() as CharacterBody3D

func _ready() -> void:
	add_to_group("enemies")

func _physics_process(delta: float) -> void:
	if body == null:
		return
	var players := get_tree().get_nodes_in_group("player")
	if players.is_empty():
		return
	var player := players[0] as Node3D
	var to_player := player.global_position - body.global_position
	to_player.y = 0.0
	var dist := to_player.length()

	if _attack_cd > 0.0:
		_attack_cd -= delta

	match state:
		AIState.CHASE:
			if dist > max(1.4, attack_range * 0.75):
				var dir := to_player.normalized()
				body.velocity.x = dir.x * speed
				body.velocity.z = dir.z * speed
				body.rotation.y = atan2(dir.x, dir.z)
			else:
				body.velocity.x = 0.0
				body.velocity.z = 0.0
				state = AIState.ATTACK
		AIState.ATTACK:
			if dist > attack_range:
				state = AIState.CHASE
			elif _attack_cd <= 0.0:
				_attack_cd = 1.6
				# Perform attack or spawn projectile via CombatSystem
				pass
		_:
			body.velocity.x = 0.0
			body.velocity.z = 0.0

	body.move_and_slide()

func take_damage(amount: float) -> void:
	hp -= amount
	if hp <= 0.0:
		body.queue_free()
