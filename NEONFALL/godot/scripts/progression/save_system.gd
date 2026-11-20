extends Node
class_name SaveSystemService

const SAVE_PATH := "user://neonfall_save.json"

var data: Dictionary = {
	"credits": 200,
	"selected_class": "berserker",
	"unlocked_classes": ["berserker", "engenheiro", "atirador"],
	"total_kills": 0,
	"best_seconds": 0
}

func _ready() -> void:
	load_save()

func save_game() -> void:
	var f := FileAccess.open(SAVE_PATH, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(data, "\t"))

func load_save() -> void:
	if not FileAccess.file_exists(SAVE_PATH):
		return
	var f := FileAccess.open(SAVE_PATH, FileAccess.READ)
	var parsed = JSON.parse_string(f.get_as_text())
	if parsed is Dictionary:
		data.merge(parsed, true)
