# Regras do R8 para o release (#37). Debug não minifica.
# O projeto não usa Supabase-kt, Ktor nem kotlinx.serialization (rede é HttpURLConnection + org.json,
# do próprio Android) e não tem reflexão própria: sons são R.raw.* direto e enums são gravados por
# `name`. Por isso as regras manuais são poucas. AdMob/UMP, Billing, Firebase Messaging e App Update
# trazem as próprias regras de consumidor nas AARs. Se o R8 reclamar de classe ausente, usar
# -dontwarn pontual; nunca -dontobfuscate geral.

# Pilha legível no Play Console: o mapa (mapping.txt) vai dentro do .aab e a Play desofusca sozinha.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Play Age Signals 0.0.4 (beta): a AAR não traz proguard.txt. Biblioteca pequena e beta, não vale
# arriscar renomear: o SDK conversa com a Play por AIDL/Parcelable.
-keep class com.google.android.play.agesignals.** { *; }

# Credential Manager 1.3.0 (login com Google): regra da documentação oficial. Sem ela o provedor
# do Google Play Services não é encontrado e o login quebra só no release.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# Nomes das exceções do Credential Manager: o app mostra `e::class.simpleName` na mensagem de falha do
# login (GoogleAuth.android.kt), que iria para o feedback ofuscada.
-keepnames class androidx.credentials.exceptions.**
