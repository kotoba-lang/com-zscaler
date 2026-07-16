(*
  bench_pid_loop.st — seigyo R1 reference program (ADR-2606111100 §1)

  ONE benchtop loop: <=2 kW electric resistance mock furnace, temperature PID.
  igata R1 die-preheat analog (220 degC class, ADR-2605261215 §6 die_preparation).

  Target runtime: OpenPLC (IEC 61131-3 Structured Text).
  Canonical committed form per ADR-2606111000 §4 — this file's CID is what
  seigyo.plcProgramAttestation signs; the runtime hash watch compares against it.

  SAFETY (ADR-2606111000 §3): the E-stop chain and the over-temperature
  thermal fuse are HARDWIRED (L1S) and physically interrupt heater power
  UPSTREAM of the SSR. Nothing in this program is in the safety path.
  The interlock inputs below are READ-ONLY status mirrors for logging.

  ENVELOPE (§3.4): the constants in the ENVELOPE block are compiled into
  this program and mirrored in bench_pid_loop.envelope.json. A setpoint
  request outside the envelope is clamped HERE, regardless of what the
  OPC UA / L3 layer requested. Changing any ENVELOPE constant is a new
  program version requiring re-attestation.
*)

PROGRAM bench_pid_loop
  VAR
    (* ---- I/O points (seigyo.ioPointRegistry: BENCH-R1, <=10 points) ---- *)
    TC_FURNACE      AT %IW0 : INT;    (* K-type via MAX31855, 0.25 degC counts *)
    ESTOP_OK_MIRROR AT %IX0.0 : BOOL; (* L1S chain status mirror — READ ONLY  *)
    FUSE_OK_MIRROR  AT %IX0.1 : BOOL; (* thermal-fuse status mirror — READ ONLY *)
    DOOR_CLOSED     AT %IX0.2 : BOOL; (* furnace door switch                  *)
    SSR_HEATER      AT %QX0.0 : BOOL; (* SSR gate (PWM); L1S cuts power above *)
    LAMP_RUN        AT %QX0.1 : BOOL;
    LAMP_ALARM      AT %QX0.2 : BOOL;

    (* ---- Northbound-writable (OPC UA, via seigyo_opcua_bridge) ---- *)
    sp_request      : REAL := 25.0;   (* requested setpoint, degC *)
    loop_enable     : BOOL := FALSE;

    (* ---- ENVELOPE — attested constants (§3.4) ---- *)
    ENV_SP_MIN      : REAL := 0.0;    (* degC *)
    ENV_SP_MAX      : REAL := 250.0;  (* degC — die-preheat 220 + margin; furnace rated 400 *)
    ENV_RATE_MAX    : REAL := 5.0;    (* degC per minute, setpoint slew *)
    ENV_PWM_MAX     : REAL := 80.0;   (* % duty ceiling — 2 kW element derate *)

    (* ---- Internal state ---- *)
    sp_active       : REAL := 25.0;   (* slew-limited, clamped setpoint *)
    pv              : REAL;           (* process value, degC *)
    pid_out         : REAL;           (* 0..100 % *)
    clamp_count     : DINT := 0;      (* envelope clamp events — silenSeigyoReview input *)
    pid_ctrl        : PID;
    pwm_timer       : TON;
    PWM_PERIOD      : TIME := T#2s;
    cycle_ms        : REAL := 100.0;  (* task interval, ms *)
  END_VAR

  (* ---- 1. Process value ---- *)
  pv := INT_TO_REAL(TC_FURNACE) * 0.25;

  (* ---- 2. Envelope clamp (defense at L1 — ALWAYS wins, §3.4) ---- *)
  IF sp_request > ENV_SP_MAX THEN
    sp_request := ENV_SP_MAX;
    clamp_count := clamp_count + 1;
  ELSIF sp_request < ENV_SP_MIN THEN
    sp_request := ENV_SP_MIN;
    clamp_count := clamp_count + 1;
  END_IF;

  (* ---- 3. Setpoint slew limit (ENV_RATE_MAX degC/min) ---- *)
  IF sp_request > sp_active THEN
    sp_active := MIN(sp_request, sp_active + ENV_RATE_MAX * cycle_ms / 60000.0);
  ELSE
    sp_active := MAX(sp_request, sp_active - ENV_RATE_MAX * cycle_ms / 60000.0);
  END_IF;

  (* ---- 4. Run condition — operational gating only; L1S is upstream ---- *)
  (* Door open or interlock-mirror fault stops MODULATION. Actual power     *)
  (* interruption on E-stop/fuse is hardwired and does not depend on this.  *)
  IF NOT (loop_enable AND ESTOP_OK_MIRROR AND FUSE_OK_MIRROR AND DOOR_CLOSED) THEN
    pid_out := 0.0;
    SSR_HEATER := FALSE;
    LAMP_RUN := FALSE;
    LAMP_ALARM := NOT (ESTOP_OK_MIRROR AND FUSE_OK_MIRROR);
    RETURN;
  END_IF;

  (* ---- 5. PID (OpenPLC standard PID FB; gains from R1 step test) ---- *)
  pid_ctrl(AUTO := TRUE, PV := pv, SP := sp_active,
           KP := 8.0, TR := 120.0, TD := 10.0,
           Y_MIN := 0.0, Y_MAX := ENV_PWM_MAX);
  pid_out := pid_ctrl.Y;

  (* ---- 6. Slow PWM onto SSR ---- *)
  pwm_timer(IN := TRUE, PT := PWM_PERIOD);
  IF pwm_timer.Q THEN
    pwm_timer(IN := FALSE);
  END_IF;
  SSR_HEATER := TIME_TO_REAL(pwm_timer.ET) / TIME_TO_REAL(PWM_PERIOD) * 100.0 < pid_out;

  LAMP_RUN := TRUE;
  LAMP_ALARM := FALSE;
END_PROGRAM
