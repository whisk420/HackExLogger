val text = """
                                                                                                    CONNECTED
                                                                                                    
                                                                                                    DISCONNECT
                                                                                                    > ÂLPHA _
                                                                                                    
                                                                                                    
                                                                                                    LVL 30
                                                                                                    //
                                                                                                    REP 10,565
                                                                                                    31,262 
                                                                                                    SCORE
                                                                                                    
                                                                                                    // XP PROGRESS
                                                                                                    15488 / 24079
                                                                                                    
                                                                                                    
                                                                                                    64%
                                                                                                    
                                                                                                    WALLET
                                                                                                    APPS
                                                                                                    PROCESSES
                                                                                                    LOG
                                                                                                    CREWS
                                                                                                    
                                                                                                    // SYSTEM INFO
                                                                                                    IP
                                                                                                    46.218.66.245
                                                                                                    DEVICE
                                                                                                    Bolt II
                                                                                                    NETWORK
                                                                                                    5G+
                                                                                                    FIREWALL
                                                                                                    Lv.20
                                                                                                    ENCRYPTOR
                                                                                                    Lv.20
"""

val ipMatch = Regex("""IP\s+([0-9]{1,3}(?:\.[0-9]{1,3}|\.xxx){3})""", RegexOption.IGNORE_CASE).find(text)
println("IP Match: ${ipMatch?.groupValues?.get(1)}")

val userLineMatch = Regex("""^>\s*(.+?)$""", RegexOption.MULTILINE).find(text)
var username = userLineMatch?.groupValues?.get(1)?.trim()
println("Username Match: $username")

val clanMatch = Regex("""\[([a-zA-Z0-9_-]{2,6})\]""").find(username ?: "")
println("Clan Match: ${clanMatch?.groupValues?.get(1)}")

val lvlMatch = Regex("""LVL\s+(\d+)""", RegexOption.IGNORE_CASE).find(text)
println("Level Match: ${lvlMatch?.groupValues?.get(1)}")

val devMatch = Regex("""DEVICE\s+([^\n\r]+)""", RegexOption.IGNORE_CASE).find(text)
println("Device Match: ${devMatch?.groupValues?.get(1)}")

val netMatch = Regex("""NETWORK\s+([^\n\r]+)""", RegexOption.IGNORE_CASE).find(text)
println("Network Match: ${netMatch?.groupValues?.get(1)}")

val fwMatch = Regex("""FIREWALL\s+Lv\.?\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)
println("FW Match: ${fwMatch?.groupValues?.get(1)}")

val encMatch = Regex("""ENCRYPTOR\s+Lv\.?\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)
println("ENC Match: ${encMatch?.groupValues?.get(1)}")
