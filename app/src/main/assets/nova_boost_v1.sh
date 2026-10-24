#!/system/bin/sh
# =====================================================================
#  NOVA BOOST V1  -  KENPOL   (dipakai oleh aplikasi DREAM)
#  OTG Bypass (GG Mouse Pro 2/3/Flado, Nebula, SensiX, Red Corner)
#  + WhatsApp / WhatsApp Business / Discord dijaga  + Free Fire / FF MAX
#  Jalankan lewat shell ADB / Shizuku (uid shell). Tanpa root.
#
#    sh nova_boost_v1.sh             KENPOL: semua fitur aktif
#    sh nova_boost_v1.sh -s          mode aman: tanpa force-stop, GOS, blok background
#    sh nova_boost_v1.sh status      lihat kondisi sekarang (tidak mengubah apa pun)
#    sh nova_boost_v1.sh find kata   cari nama paket terpasang (mis. find sensi red)
#    sh nova_boost_v1.sh list        daftar semua app pihak ketiga
#    sh nova_boost_v1.sh off         kembalikan pengaturan seperti semula
# =====================================================================

# ----------------------------- KONFIGURASI ---------------------------
# APP YANG DILINDUNGI: tidak pernah di-kill / force-stop, selalu lolos Doze
# dan batas baterai. Pola regex, tidak peka huruf besar/kecil; hanya yang
# terpasang yang diproses (hasil deteksi dicetak tiap kali script jalan).
RX_GGMP='gg.*mouse|ggmp|gg\.mp|flado'   # GG Mouse Pro 2 / 3 / varian Flado
RX_NEBULA='nebula'                       # Nebula Touch / Nebula Mouse
RX_SENSIX='sensi.?x'                     # SensiX
RX_REDC='red.?corner'                    # Red Corner
RX_WA='^com\.whatsapp$'                  # WhatsApp Messenger
RX_WAB='^com\.whatsapp\.w4b$'            # WhatsApp Business
RX_DISCORD='^com\.discord$'              # Discord
KEEP_EXTRA=${KEEP_EXTRA:-}               # tambahan paket persis, pisah spasi
GAMES='freefire|mobile\.legends'         # game (Free Fire, FF MAX, Mobile Legends)

# Ikut dilindungi (alat terminal/Shizuku + VPN/proxy/Discord apa pun)
KEEP="moe.shizuku.privileged.api me.piebridge.brevent com.termux in.sunilpaulmathew.ashell $KEEP_EXTRA"
KEEP_PATTERN='vpn|proxy|wireguard|clash|v2ray|xray|shadowsocks|discord'

NOBG="com.android.vending"               # dilarang jalan di background saat boost
GOS_PKGS="com.samsung.android.game.gos com.enhance.gameservice"

DND_MODE=${DND_MODE:-priority}           # priority | alarms | none | off (tanpa DND)
ANIM=${ANIM:-0.5}                        # skala animasi sistem (0 = mati, 1 = normal)
POINTER_SPEED=${POINTER_SPEED:-}         # kecepatan kursor mouse OTG, -7..7 (kosong = tidak diubah)
HOT_T=${HOT_T:-420}                      # batas suhu baterai (per 0.1 C): 420 = 42.0 C
AUTO_LAUNCH=${AUTO_LAUNCH:-}             # mis. com.dts.freefiremax (kosong = tidak buka apa pun)

# Saklar fitur: 1 = aktif, 0 = lewati
F_KILL=${F_KILL:-1}     # force-stop app pihak ketiga yang tidak dilindungi
F_GOS=${F_GOS:-1}       # Samsung: matikan Game Optimizing Service (HP bisa lebih panas)
F_NOBG=${F_NOBG:-1}     # blok app di NOBG jalan di background
F_NET=${F_NET:-1}       # Wi-Fi low-latency + matikan scan background
F_ANIM=${F_ANIM:-1}     # percepat animasi sistem
F_DEX=${F_DEX:-1}       # kompilasi AOT alat & game (pertama kali agak lama)
F_KBD=${F_KBD:-1}       # keyboard OTG: keyboard layar tidak muncul
F_STAYON=${F_STAYON:-0} # layar tetap menyala saat dicas (opsional)
# --- saklar tambahan dari aplikasi DREAM ---
F_PHANTOM=${F_PHANTOM:-1}   # langkah 1: Phantom Process Killer OFF
F_DOZE=${F_DOZE:-1}         # langkah 2: whitelist Doze + batas baterai
F_OTG=${F_OTG:-1}           # langkah 3: OTG bypass
F_FREEZER=${F_FREEZER:-1}   # langkah 4: anti-kill latar belakang
F_HZ=${F_HZ:-1}             # langkah 5: kunci refresh rate
F_GAME=${F_GAME:-1}         # langkah 6: mode performa game
F_DND=${F_DND:-1}           # langkah 7: mode DND
F_DEXSUP=${F_DEXSUP:-1}     # langkah 8: dukungan DeX / freeform
F_RAM=${F_RAM:-1}           # langkah 14: bersihkan RAM & cache

BK=${BK:-/data/local/tmp/nova_boost_v1.bak}
TMP=${TMPDIR:-/data/local/tmp}
# ---------------------------------------------------------------------

MODE=on; Q=""
for a in "$@"; do
  case "$a" in
    off) MODE=off ;;
    status) MODE=status ;;
    list) MODE=list ;;
    find) MODE=find ;;
    -s|safe) F_KILL=0; F_GOS=0; F_NOBG=0 ;;
    -a) ;;
    *) Q="$Q $a" ;;
  esac
done

MYID=$(id -u)
case "$MYID" in
  0|2000) ;;
  *) echo "Harus dijalankan sebagai shell (adb shell / Shizuku)."; exit 1 ;;
esac

say()   { echo "$*"; }
quiet() { "$@" </dev/null >/dev/null 2>&1; }
count() { echo $#; }
skip()  { say "   dilewati"; }
MEM()   { sed -n 's/^MemAvailable: *\([0-9]*\) kB.*/\1/p' /proc/meminfo; }
N=0; TOTAL=14
step()  { N=$((N+1)); say "[$N/$TOTAL] $*"; }
# simpan nilai lama sekali saja, lalu tulis nilai baru
save()   { grep -qs "^$1 $2 " "$BK" || echo "$1 $2 $(settings get "$1" "$2" 2>/dev/null)" >> "$BK"; }
sput()   { save "$1" "$2"; quiet settings put "$1" "$2" "$3"; }
dcsave() { grep -qs "^dc $1 $2 " "$BK" || echo "dc $1 $2 $(device_config get "$1" "$2" 2>/dev/null)" >> "$BK"; }
dcput()  { dcsave "$1" "$2"; quiet device_config put "$1" "$2" "$3"; }
idle_has()    { dumpsys deviceidle whitelist 2>/dev/null | grep -q ",$1,"; }
is_disabled() { pm list packages -d 2>/dev/null | grep -q "package:$1\$"; }
shizuku_up()  { ps -A 2>/dev/null | grep -q shizuku_server || ps 2>/dev/null | grep -q shizuku_server; }

TOOLS="$RX_GGMP|$RX_NEBULA|$RX_SENSIX|$RX_REDC"
COMMS="$RX_WA|$RX_WAB|$RX_DISCORD"
ALLPKG=$(pm list packages 2>/dev/null | sed 's/^package://')
TOOL_PKGS=$(echo "$ALLPKG" | grep -iE "$TOOLS")
COMM_PKGS=$(echo "$ALLPKG" | grep -iE "$COMMS")
GAME_PKGS=$(echo "$ALLPKG" | grep -iE "$GAMES")
PROTECT=""
for p in $TOOL_PKGS $COMM_PKGS $GAME_PKGS $KEEP; do
  quiet pm path "$p" && PROTECT="$PROTECT $p"
done

banner() {
  say "======================================"
  say "   NOVA BOOST V1  -  K E N P O L"
  say "======================================"
  say "Android $(getprop ro.build.version.release) / $(getprop ro.product.model)"
}

row() {  # $1 = label, $2 = regex
  m=$(echo "$ALLPKG" | grep -iE "$2")
  if [ -n "$m" ]; then say "  [OK] $1: $(echo $m)"; else say "  [--] $1: belum terdeteksi"; fi
}

show_targets() {
  say "Dilindungi (tidak ditutup di latar belakang):"
  row "GG Mouse Pro 2/3/Flado" "$RX_GGMP"
  row "Nebula Touch/Mouse" "$RX_NEBULA"
  row "SensiX" "$RX_SENSIX"
  row "Red Corner" "$RX_REDC"
  row "WhatsApp" "$RX_WA"
  row "WhatsApp Business" "$RX_WAB"
  row "Discord" "$RX_DISCORD"
  [ -n "$KEEP_EXTRA" ] && say "  [+] Tambahan: $KEEP_EXTRA"
  say "Game:"
  for p in $GAME_PKGS; do say "  - $p"; done
  [ "$(count $GAME_PKGS)" = 0 ] && say "  (belum ada yang cocok)"
}

show_status() {
  B=$(dumpsys battery 2>/dev/null)
  LV=$(echo "$B" | sed -n 's/^ *level: *//p' | head -1)
  TP=$(echo "$B" | sed -n 's/^ *temperature: *//p' | head -1)
  TPS="?"; [ -n "$TP" ] && TPS="$((TP / 10)).$((TP % 10)) C"
  case "$(settings get global zen_mode 2>/dev/null)" in
    1) DN="prioritas" ;; 2) DN="senyap total" ;; 3) DN="alarm saja" ;; *) DN="mati" ;;
  esac
  case "$(settings get global settings_enable_monitor_phantom_procs 2>/dev/null)" in
    false) PK="OFF" ;; *) PK="masih ON" ;;
  esac
  GS="-"
  for p in $GOS_PKGS; do
    quiet pm path "$p" || continue
    if is_disabled "$p"; then GS="OFF"; else GS="aktif"; fi
  done
  if shizuku_up; then SZ="AKTIF"; else SZ="tidak terdeteksi"; fi
  M=$(MEM)
  say "------------- STATUS -------------"
  say " RAM tersedia : $(( ${M:-0} / 1024 )) MB"
  say " Baterai      : ${LV:-?}% / $TPS"
  say " Refresh rate : min $(settings get system min_refresh_rate 2>/dev/null) / peak $(settings get system peak_refresh_rate 2>/dev/null)"
  say " DND          : $DN"
  say " Phantom Kill : $PK"
  say " Samsung GOS  : $GS"
  say " Shizuku      : $SZ"
  say " Dilindungi   : $(count $PROTECT) paket"
  say "----------------------------------"
}

# daftar pelindung untuk pembersihan RAM
build_keep() {
  LAUNCHER=$(cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN 2>/dev/null | tail -1 | cut -d/ -f1)
  FG=$(dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | sed -n 's#.* \([A-Za-z0-9_.]*\)/.*#\1#p')
  EXTRA=$( { settings get secure default_input_method
             settings get secure enabled_accessibility_services
             settings get secure enabled_notification_listeners; } 2>/dev/null |
           tr ':\n' '  ' | sed 's#/[^ ]*##g' )
  KEEPALL=" $PROTECT $LAUNCHER $FG $EXTRA "
}
is_kept() {
  case "$KEEPALL" in *" $1 "*) return 0 ;; esac
  echo "$1" | grep -qiE "$KEEP_PATTERN|$TOOLS|$COMMS"
}

# kill proses cache/background (aman: am kill) KECUALI yang dilindungi
kill_bg() {
  PKGF="$TMP/.nova_pk.$$"
  echo "$ALLPKG" > "$PKGF"
  KC=0
  for p in $(ps -A 2>/dev/null | sed 's/.*[[:space:]]//' | cut -d: -f1 | sort -u | grep -Fxf "$PKGF"); do
    is_kept "$p" && continue
    quiet am kill "$p"
    KC=$((KC+1))
  done
  rm -f "$PKGF"
  say "   $KC proses latar belakang dibersihkan"
}

# force-stop semua app pihak ketiga, kecuali yang dilindungi
aggressive() {
  K=0
  for p in $(pm list packages -3 2>/dev/null | sed 's/^package://'); do
    is_kept "$p" && continue
    quiet am force-stop "$p"
    K=$((K+1))
  done
  say "   $K app dihentikan (force-stop)"
}

# ===================================================================
do_on() {
  banner
  show_targets
  RAM0=$(MEM)

  # pengaman suhu: kalau HP sudah panas, lewati fitur yang menambah panas
  HOT=0
  TPG=$(dumpsys battery 2>/dev/null | sed -n 's/^ *temperature: *//p' | head -1)
  case "$TPG" in ''|*[!0-9]*) TPG=0 ;; esac
  if [ "$TPG" -ge "$HOT_T" ]; then
    HOT=1; F_GOS=0; F_DEX=0
    say "!! Suhu $((TPG / 10)).$((TPG % 10)) C (batas $((HOT_T / 10)) C): GOS-off, fixed-performance & AOT dilewati"
  fi

  step "Phantom Process Killer -> OFF"
  if [ "$F_PHANTOM" = 1 ]; then
  quiet device_config set_sync_disabled_for_tests persistent
  dcput activity_manager max_phantom_processes 2147483647
  sput global settings_enable_monitor_phantom_procs false
  else skip; fi

  step "Whitelist Doze + pembatasan baterai -> OFF"
  if [ "$F_DOZE" = 1 ]; then
  for p in $PROTECT; do
    idle_has "$p" || echo "wl $p" >> "$BK"
    quiet dumpsys deviceidle whitelist +"$p"
    quiet cmd appops set "$p" RUN_IN_BACKGROUND allow
    quiet cmd appops set "$p" RUN_ANY_IN_BACKGROUND allow
    quiet am set-standby-bucket "$p" active
  done
  sput global app_standby_enabled 0
  sput global adaptive_battery_management_enabled 0
  sput global forced_app_standby_enabled 0
  sput global app_auto_restriction_enabled false
  sput global low_power 0
  sput global low_power_trigger_level 0
  quiet cmd power set-adaptive-power-saver-enabled false
  else skip; fi

  step "OTG BYPASS - jaga alat, chat & keyboard OTG"
  if [ "$F_OTG" = 1 ]; then
  for p in $PROTECT; do quiet am set-inactive "$p" false; done
  sput global adb_allowed_connection_time 0     # izin ADB/Shizuku tidak kedaluwarsa
  sput global always_finish_activities 0        # pastikan "Don't keep activities" mati
  if [ "$F_KBD" = 1 ]; then sput secure show_ime_with_hard_keyboard 0; fi
  if [ -n "$POINTER_SPEED" ]; then sput system pointer_speed "$POINTER_SPEED"; fi
  if shizuku_up; then say "   Shizuku: AKTIF"
  else say "   Shizuku: tidak terdeteksi (nyalakan dulu kalau mapper-mu pakai Shizuku)"; fi
  else skip; fi

  step "Anti-kill latar belakang (freezer, batas proses cache)"
  if [ "$F_FREEZER" = 1 ]; then
  dcput activity_manager_native_boot use_freezer false
  sput global cached_apps_freezer disabled
  dcput activity_manager max_cached_processes 64
  say "   (pembekuan app cache baru mati penuh setelah reboot)"
  else skip; fi

  step "Kunci refresh rate ke maksimum"
  if [ "$F_HZ" = 1 ]; then
  HZ=0
  for f in $(dumpsys display 2>/dev/null | grep -m1 'supportedModes \[' | grep -oE 'fps=[0-9]+(\.[0-9]+)?' | sed 's/fps=//'); do
    i=${f%%.*}
    d=0
    case "$f" in *.*) d=$(echo "${f#*.}" | cut -c1) ;; esac
    [ "${d:-0}" -ge 5 ] && i=$((i+1))
    [ "$i" -gt "$HZ" ] && HZ=$i
  done
  [ "$HZ" -ge 60 ] || HZ=120
  sput system min_refresh_rate "$HZ.0"
  sput system peak_refresh_rate "$HZ.0"
  say "   target ${HZ}Hz"
  [ "$(settings get system min_refresh_rate 2>/dev/null)" = "$HZ.0" ] ||
    say "   (ROM menolak - atur manual di Pengaturan > Layar > Refresh rate)"
  else skip; fi

  step "Mode performa game"
  if [ "$F_GAME" = 1 ]; then
  for p in $GAME_PKGS; do
    quiet cmd game mode performance "$p" || quiet cmd game set --mode performance "$p"
  done
  if [ "$HOT" = 0 ]; then quiet cmd power set-fixed-performance-mode-enabled true; else skip; fi
  else skip; fi

  step "Mode DND ($DND_MODE)"
  if [ "$F_DND" = 1 ]; then
  if [ "$DND_MODE" = off ]; then skip
  else
    save global zen_mode
    quiet cmd notification set_dnd "$DND_MODE"
    if [ "$(settings get global zen_mode 2>/dev/null)" = 0 ]; then
      case "$DND_MODE" in none) Z=2 ;; alarms) Z=3 ;; *) Z=1 ;; esac
      quiet settings put global zen_mode "$Z"
    fi
  fi
  else skip; fi

  step "Dukungan DeX / jendela desktop"
  if [ "$F_DEXSUP" = 1 ]; then
  sput global enable_freeform_support 1
  sput global force_resizable_activities 1
  sput global enable_non_resizable_multi_window 1
  case "$(getprop ro.product.manufacturer | tr 'A-Z' 'a-z')" in
    samsung) ;;  # DeX bawaan Samsung sudah ada
    *) sput global force_desktop_mode_on_external_displays 1 ;;
  esac
  if [ "$F_STAYON" = 1 ]; then sput global stay_on_while_plugged_in 7; fi
  else skip; fi

  step "Jaringan: Wi-Fi low-latency + stop scan background"
  if [ "$F_NET" = 1 ]; then
    quiet cmd wifi force-hi-perf-mode enabled
    quiet cmd wifi force-low-latency-mode enabled
    sput global wifi_scan_always_enabled 0
    sput global ble_scan_always_enabled 0
  else skip; fi

  step "Samsung GOS (anti-throttle) -> OFF"
  if [ "$F_GOS" = 1 ]; then
    FOUND=0
    for p in $GOS_PKGS; do
      quiet pm path "$p" || continue
      FOUND=1
      is_disabled "$p" || echo "pkgdis $p" >> "$BK"
      quiet pm disable-user --user 0 "$p"
      quiet am force-stop "$p"
      if is_disabled "$p"; then say "   $p: OFF (HP bisa lebih panas)"
      else say "   $p: gagal dimatikan"; fi
    done
    [ "$FOUND" = 0 ] && say "   tidak ada di HP ini"
  else skip; fi

  step "Animasi sistem ${ANIM}x"
  if [ "$F_ANIM" = 1 ]; then
    sput global window_animation_scale "$ANIM"
    sput global transition_animation_scale "$ANIM"
    sput global animator_duration_scale "$ANIM"
  else skip; fi

  step "Blok app background ($NOBG)"
  if [ "$F_NOBG" = 1 ]; then
    for p in $NOBG; do
      quiet pm path "$p" || continue
      quiet cmd appops set "$p" RUN_IN_BACKGROUND deny
      quiet cmd appops set "$p" RUN_ANY_IN_BACKGROUND deny
      quiet am force-stop "$p"
    done
  else skip; fi

  step "Kompilasi AOT alat & game (pertama kali bisa lama)"
  if [ "$F_DEX" = 1 ]; then
    for p in $TOOL_PKGS $GAME_PKGS; do quiet cmd package compile -m speed "$p"; done
  else skip; fi

  step "Bersihkan RAM & cache (app dilindungi tidak disentuh)"
  if [ "$F_RAM" = 1 ]; then
  build_keep
  kill_bg
  quiet pm trim-caches 999G
  if [ "$MYID" = 0 ]; then sync; echo 3 > /proc/sys/vm/drop_caches; fi
  if [ "$F_KILL" = 1 ]; then aggressive; else say "   (force-stop dilewati)"; fi
  sleep 2
  M=$(MEM)
  say "   RAM tersedia: $(( ${RAM0:-0} / 1024 )) MB -> $(( ${M:-0} / 1024 )) MB"
  else skip; fi

  show_status
  say "SELESAI. Batalkan lewat tombol Kembalikan di aplikasi."
  if [ -n "$AUTO_LAUNCH" ]; then
    say "Membuka $AUTO_LAUNCH ..."
    quiet monkey -p "$AUTO_LAUNCH" -c android.intent.category.LAUNCHER 1
  fi
}

# ===================================================================
do_off() {
  say "== NOVA BOOST V1 - KEMBALIKAN PENGATURAN =="
  if [ -f "$BK" ]; then
    while read -r k1 k2 k3 k4; do
      VAL="$k3${k4:+ $k4}"
      case "$k1" in
        wl) quiet dumpsys deviceidle whitelist -"$k2" ;;
        pkgdis) quiet pm enable --user 0 "$k2" ;;
        dc)
          if [ -z "$k4" ] || [ "$k4" = null ]; then
            quiet device_config delete "$k2" "$k3"
          else
            quiet device_config put "$k2" "$k3" "$k4"
          fi ;;
        global|system|secure)
          if [ -z "$VAL" ] || [ "$VAL" = null ]; then
            quiet settings delete "$k1" "$k2"
          else
            quiet settings put "$k1" "$k2" "$VAL"
          fi ;;
      esac
    done < "$BK"
    rm -f "$BK"
  else
    say "  (tidak ada backup nilai lama)"
  fi
  # sisa dari versi lama yang belum menyimpan nilai asli
  [ "$(device_config get activity_manager max_phantom_processes 2>/dev/null)" = 2147483647 ] &&
    quiet device_config delete activity_manager max_phantom_processes
  quiet device_config set_sync_disabled_for_tests none
  quiet cmd wifi force-hi-perf-mode disabled
  quiet cmd wifi force-low-latency-mode disabled
  for p in $PROTECT $NOBG; do
    quiet cmd appops set "$p" RUN_IN_BACKGROUND default
    quiet cmd appops set "$p" RUN_ANY_IN_BACKGROUND default
  done
  for p in $GAME_PKGS; do quiet cmd game mode standard "$p"; done
  quiet cmd power set-fixed-performance-mode-enabled false
  say "Selesai (kompilasi AOT dibiarkan, tidak merugikan)."
  say "Sebagian pengaturan baru penuh setelah reboot."
}

do_status() { banner; show_targets; show_status; }
do_list()   { say "App pihak ketiga terpasang:"; pm list packages -3 2>/dev/null | sed 's/^package:/  /' | sort; }
do_find() {
  if [ -z "$Q" ]; then say "Pakai: sh $0 find <kata> [kata2 ...]"; exit 1; fi
  for w in $Q; do
    say "== $w"
    echo "$ALLPKG" | grep -i -- "$w" | sed 's/^/  /'
  done
}

case "$MODE" in
  off) do_off ;;
  status) do_status ;;
  list) do_list ;;
  find) do_find ;;
  *) do_on ;;
esac
