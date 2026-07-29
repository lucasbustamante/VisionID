# As APIs dos fabricantes são acessadas por reflexão para suportar firmwares diferentes.
-keep class com.newland.** { *; }
-dontwarn com.newland.**
-keep class com.xcheng.printerservice.** { *; }
