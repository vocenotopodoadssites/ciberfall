extends Node
class_name CombatSystem

@export var auto_attack: bool = true
var attack_damage: float = 28.0
var attack_cooldown: float = 0.55
var attack_range: float = 12.0
var _timer: float = 0.0

func configure(char_data: Dictionary) -> void:
	var stats: Dictionary = char_data.get("stats", {})
	attack_damage = float(stats.get("attackDamage", 25.0))
	attack_cooldown = float(stats.get("attackCooldown", 0.5))
	attack_range = float(stats.get("attackRange", 12.0))

func _process(delta: float) -> void:
	_timer -= delta
	if auto_attack and _timer <= 0.0:
		var target := _find_nearest_enemy()
		if target:
			_timer = attack_cooldown
			if target.has_method("take_damage"):
				target.take_damage(attack_damage)

func trigger_active_ability() -> void:
	pass

func _find_nearest_enemy() -> Node3D:
	var owner_node := get_parent() as Node3D
	var best: Node3D = null
	var best_dist := attack_range
	for e in get_tree().get_nodes_in_group("enemies"):
		if e is Node3D:
			var d := owner_node.global_position.distance_to(e.global_position)
			if d <= best_dist:
				best_dist = d
				best = e
	return best
