package felis.launcher

class GameNotFound(name: String) : Exception("Game '$name' could not be found in the classpath")