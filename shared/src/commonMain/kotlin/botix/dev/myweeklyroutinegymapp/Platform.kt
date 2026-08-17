package botix.dev.myweeklyroutinegymapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform