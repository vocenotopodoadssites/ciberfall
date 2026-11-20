extends Node3D
class_name ThirdPersonCameraRig

## Third-person SpringArm3D camera rig with mouse orbit, zoom adjustment, and wall collision avoidance.

@export var mouse_sensitivity: float = 0.003
@export var min_pitch_deg: float = -45.0
@export var max_pitch_deg: float = 20.0
@export var min_distance: float = 4.5
@export var max_distance: float = 11.0

@onready var spring_arm: SpringArm3D = $SpringArm3D

var _yaw: float = 0.0
var _pitch: float = -0.38

func _ready() -> void:
	Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)
	if spring_arm:
		spring_arm.spring_length = 7.4

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion and Input.get_mouse_mode() == Input.MOUSE_MODE_CAPTURED:
		_yaw -= event.relative.x * mouse_sensitivity
		_pitch = clamp(_pitch - event.relative.y * mouse_sensitivity, deg_to_rad(min_pitch_deg), deg_to_rad(max_pitch_deg))
		rotation.y = _yaw
		if spring_arm:
			spring_arm.rotation.x = _pitch
	elif event is InputEventMouseButton and event.pressed:
		if event.button_index == MOUSE_BUTTON_WHEEL_UP and spring_arm:
			spring_arm.spring_length = clamp(spring_arm.spring_length - 0.5, min_distance, max_distance)
		elif event.button_index == MOUSE_BUTTON_WHEEL_DOWN and spring_arm:
			spring_arm.spring_length = clamp(spring_arm.spring_length + 0.5, min_distance, max_distance)
	elif event.is_action_pressed("ui_cancel"):
		var mode := Input.get_mouse_mode()
		Input.set_mouse_mode(Input.MOUSE_MODE_VISIBLE if mode == Input.MOUSE_MODE_CAPTURED else Input.MOUSE_MODE_CAPTURED)
