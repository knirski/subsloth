package net.subsloth.database

import androidx.room3.RoomDatabaseConstructor

expect object SubSlothDatabaseCtor : RoomDatabaseConstructor<SubSlothDatabase>

expect fun createSubSlothDatabase(name: String): SubSlothDatabase
