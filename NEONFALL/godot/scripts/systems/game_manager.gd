extends Node
class_name GameManagerService

var current_xp: int = 0
var xp_to_next: int = 80
var level: int = 1
var active_cards: Dictionary = {}

func add_xp(amount: int) -> Array:
	current_xp += amount
	if current_xp >= xp_to_next:
		current_xp -= xp_to_next
		level += 1
		xp_to_next = int(xp_to_next * 1.28 + 35)
		return roll_cards(3)
	return []

func roll_cards(count: int) -> Array:
	var pool := DataLoader.cards.duplicate()
	pool.shuffle()
	return pool.slice(0, min(count, pool.size()))
