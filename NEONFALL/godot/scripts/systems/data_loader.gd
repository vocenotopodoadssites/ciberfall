extends Node
class_name DataLoaderService

var characters: Array = []
var enemies: Array = []
var bosses: Array = []
var cards: Array = []
var skins: Array = []
var abilities: Dictionary = {}

func _ready() -> void:
	characters = _load_json_dict("res://../data/characters.json").get("classes", [])
	var enemy_root := _load_json_dict("res://../data/enemies.json")
	enemies = enemy_root.get("enemies", [])
	bosses = enemy_root.get("bosses", [])
	cards = _load_json_dict("res://../data/cards.json").get("cards", [])
	skins = _load_json_dict("res://../data/skins.json").get("skins", [])
	for ab in _load_json_dict("res://../data/abilities.json").get("abilities", []):
		abilities[ab["id"]] = ab

func get_character(id: String) -> Dictionary:
	for c in characters:
		if c.get("id", "") == id:
			return c
	return {}

func _load_json_dict(path: String) -> Dictionary:
	if not FileAccess.file_exists(path):
		return {}
	var file := FileAccess.open(path, FileAccess.READ)
	var parsed = JSON.parse_string(file.get_as_text())
	return parsed if parsed is Dictionary else {}
