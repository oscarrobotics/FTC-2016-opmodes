package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.util.ElapsedTime;

import static org.firstinspires.ftc.teamcode.AutoStates.State.*;

@com.qualcomm.robotcore.eventloop.opmode.Autonomous(name = "Oscar: AutoStates", group = "Oscar")
public class AutoStates extends BaseOp {
    public int position, encoderTarget;
    public double drivePower;
    public double speed;
    public double direction;

    public enum State { // Ideally, these stay in order of how we use them
        STATE_INITIAL,
        STATE_FIRST_SHOT,
        STATE_FIRST_LOAD,
        STATE_WAIT_FOR_LOAD,
        STATE_SECOND_SHOT,
        STATE_DRIVE_BACKWARDS1,
        STATE_TURN_45_1,
        STATE_DRIVE_BACKWARDS2,
        STATE_TURN_45_2,
        STATE_MOVE_LEFT,
        STATE_DRIVE_FOR_BEACON1,
        STATE_DRIVE_IN_BETWEEN_BEACONS,
        STATE_DRIVE_LEFT2,
        STATE_DRIVE_FOR_BEACON2,
        STATE_DRIVE_AFTER_BEACONS,
        STATE_CAP_DRIVE1,
        STATE_CAP_TURN1,
        STATE_TURN_FOR_PARK,
        STATE_DRIVE_FOR_PARK,
        STATE_DRIVE_FOR_PARK_RED,
        STATE_STOP
    }

    private ElapsedTime mStateTime = new ElapsedTime();  // Time into current state
    private State mCurrentState;
    private boolean yPressed = false;
    private boolean wallSensorTriggered = false;
    private double SlowBoost = 0.0;

    public enum autoMode {
        MODE_DRIVE_BEACONS,
        MODE_DRIVE_BEACONS_ONE_SHOT,
        MODE_SHOOT_ONLY
    }

    public autoMode currentMode = autoMode.MODE_DRIVE_BEACONS;

    @Override
    public void init_loop() {
        super.init_loop();

        if (gamepad2.y && !yPressed) {
            switch (currentMode) {
                case MODE_DRIVE_BEACONS:
                    currentMode = autoMode.MODE_DRIVE_BEACONS_ONE_SHOT;
                    break;
                case MODE_DRIVE_BEACONS_ONE_SHOT:
                    currentMode = autoMode.MODE_SHOOT_ONLY;
                    break;
                case MODE_SHOOT_ONLY:
                    currentMode = autoMode.MODE_DRIVE_BEACONS;
                    break;
            }

        }

        if (gamepad1.dpad_up) {
            SlowBoost += 0.01;
        }
        if (gamepad1.dpad_down) {
            SlowBoost -= 0.01;
        }
        yPressed = gamepad2.y;

        if (gamepad2.b) isRed = true;
        if (gamepad2.x) isRed = false;
        telemetry.addData("Slow boost", SlowBoost);
        telemetry.addData("2", currentMode);
        telemetry.addData("3", isRed ? "RedSide" : "BlueSide");
    }

    @Override
    public void start() {
        resetStartTime();
        newState(State.STATE_INITIAL);
    }


    @Override
    public void init() {
        super.init();


        // Init variables
        position = shooter.getCurrentPosition();
        encoderTarget = 4950; // encoder target in native Ticks
        drivePower = 1.0;
        loader.setPosition(loaderStatic); // added to resolve loader servo not initialized properly - george
        beaconPress.setPosition(servoIn);

        // Init commands TODO: what am i?
        // init_loop();
    }

    public void loop() {
        if (beaconEnabled) {
            boolean extendBeaconPress = (isRed && redBlueSensor.blue() + colorSensorMargin < redBlueSensor.red() ||
                    (!isRed && redBlueSensor.blue() > redBlueSensor.red() + colorSensorMargin));
            if (redBlueSensor.blue() + colorSensorMargin < redBlueSensor.red() || redBlueSensor.red() + colorSensorMargin < redBlueSensor.red()) {
                if (redBlueSensor.blue() + colorSensorMargin < redBlueSensor.red())
                    beaconColor = 1;
                if (redBlueSensor.red() + colorSensorMargin < redBlueSensor.blue())
                    beaconColor = 2;
            } else {
                beaconColor = 0;
            }
            if (extendBeaconPress) {
                beaconPress.setPosition(isRed ? servoOpposite : servoExtend);
                bringBackInAt = System.currentTimeMillis() + retractDelay;
            } else if (bringBackInAt < System.currentTimeMillis()) {
                beaconPress.setPosition(isRed ? servoOppositeIn : servoIn);
            }
        } else {
            if (nearBeacon) {
                beaconPress.setPosition(isRed ? servoOppositeIn : servoIn);
            } else {
                beaconPress.setPosition(servoIn);
            }
        }
        super.loop();
        // Telemetry block
        //telemetry.addLine()
        // .addData("Loop count", loopCounter)
        //.addData("State", mCurrentState);
        // telemetry.addData("3", "Light detected: " + odSensor.getLightDetected());
        switch (mCurrentState) {
            case STATE_INITIAL:
                if (currentMode == autoMode.MODE_DRIVE_BEACONS_ONE_SHOT)
                    newState(STATE_SECOND_SHOT);
                else
                    newState(STATE_FIRST_SHOT);
                shooterTargetPosition -= shooterRotation;
                shooter.setTargetPosition(shooterTargetPosition);
                break;

            case STATE_FIRST_SHOT:
                if (!shooter.isBusy()) {
                    loader.setPosition(loaderLoad);
                    newState(STATE_FIRST_LOAD);
                }
                break;

            case STATE_FIRST_LOAD:
                if (loader.getPosition() == loaderLoad) {
                    newState(STATE_WAIT_FOR_LOAD);
                }
                break;

            case STATE_WAIT_FOR_LOAD:
                if (mStateTime.milliseconds() > 1250) {
                    loader.setPosition(loaderStatic);
                    shooterTargetPosition -= shooterRotation;
                    shooter.setTargetPosition(shooterTargetPosition);
                    newState(STATE_SECOND_SHOT);
                }
                break;

            case STATE_SECOND_SHOT:
                // if (!shooter.isBusy() {
                if (!shooter.isBusy() || shooter.getCurrentPosition() - (shooterRotation * 0.75) < shooterTargetPosition) {
                    if (currentMode == autoMode.MODE_DRIVE_BEACONS) {
                        newState(STATE_DRIVE_BACKWARDS1);
                    } else if (currentMode == autoMode.MODE_SHOOT_ONLY) {
                        newState(STATE_STOP);
                    }
                }
                break;


            case STATE_DRIVE_BACKWARDS1:
                speed = isRed ? 1 : .75;
                if (MecanumDrive(speed, isRed ? forwardMove(speed) : backwardMove(speed), rotationComp(), isRed ? -700 : 1100)) {
                    newState(STATE_TURN_45_1);
                    MecanumDrive(0, 0, rotationComp(), 0);
                }

                break;

            case STATE_TURN_45_1:
                targetHeading = isRed ? 45 : 313;
                MecanumDrive(0, 0, rotationComp(), 0);
                if (gyroCloseEnough(3)) {
                    newState(STATE_DRIVE_BACKWARDS2);
                }
                break;

            case STATE_DRIVE_BACKWARDS2:
                speed = 1;
                if (MecanumDrive(speed, isRed ? forwardMove(speed) : backwardMove(speed), rotationComp(), isRed ? -4900 : 5000)) {
                    nearBeacon = true;
                    newState(STATE_TURN_45_2);
                    MecanumDrive(0, 0, rotationComp(), 0);
                }
                break;

            case STATE_TURN_45_2:
                targetHeading = isRed ? 88 : 270;
                MecanumDrive(0, 0, rotationComp(), 0);
                if (gyroCloseEnough(3)) {
                    touchSensorServo.setPosition(touchSensorDown);
                    newState(STATE_MOVE_LEFT);
                }
                break;

            case STATE_MOVE_LEFT:
                speed = .7;

                if (MecanumDrive(speed, leftMove(speed), rotationComp(), isRed ? 1700 : 1500) || touchSensor.isPressed()) {
                    if (touchSensor.isPressed()) wallSensorTriggered = true;
                    MecanumDrive(0, 0, 0, 0);
                    targetDestination = 0;
                    touchSensorServo.setPosition(touchSensorUp);
                    newState(STATE_DRIVE_FOR_BEACON1);
                }
                break;

            case STATE_DRIVE_FOR_BEACON1:
                beaconEnabled = true;
                speed = isRed ? .25 : .3; // TODO: This can be faster if we're sure we're close to wall; can also change beacon servo range to be tighter
                if (wallSensorTriggered) speed += SlowBoost;
//                if (MecanumDrive(speed, isRed ? forwardMove(speed) : backwardMove(speed), rotationComp(), isRed? -1500 : 2000)) {
//                    newState(STATE_DRIVE_IN_BETWEEN_BEACONS);
//                }
                if (lastBeaconColor != 0 && lastBeaconColor != beaconColor) {
                    passedBeacon = true;
                }
                if (passedBeacon) {
                    if (MecanumDrive(speed, isRed ? forwardMove(speed) : backwardMove(speed), rotationComp(), isRed ? -100 : 100)) {
                        targetDestination = 0;
                        MecanumDrive(0, 0, 0, 0);
                        newState(STATE_DRIVE_IN_BETWEEN_BEACONS);
                    }
                }
                lastBeaconColor = beaconColor;
                break;

            case STATE_DRIVE_IN_BETWEEN_BEACONS:
                speed = 1;
                if (MecanumDrive(speed, isRed ? forwardMove(speed) : backwardMove(speed), rotationComp(), isRed ? -2700 : 2750)) {
                    newState(STATE_DRIVE_FOR_BEACON2);
                }
                break;

            case STATE_DRIVE_LEFT2:
                speed = .7;

                if (MecanumDrive(speed, leftMove(speed), rotationComp(), 10000) || touchSensor.isPressed()) {
                    targetDestination = 0;
                    MecanumDrive(0, 0, 0, 0);
                    touchSensorServo.setPosition(touchSensorUp);
                    newState(STATE_DRIVE_FOR_BEACON2);
                }
                break;

            case STATE_DRIVE_FOR_BEACON2:
                beaconEnabled = true;
                speed = isRed ? .25 : .3;
                if (wallSensorTriggered) speed += SlowBoost;
                if (MecanumDrive(speed, isRed ? forwardMove(speed) : backwardMove(speed), rotationComp(), isRed ? -1600 : 1600)) {
                    newState(STATE_DRIVE_AFTER_BEACONS);
                }
                break;

            case STATE_DRIVE_AFTER_BEACONS:
                beaconEnabled = false;
                speed = 1;
                if (MecanumDrive(speed, isRed ? forwardMove(speed) : backwardMove(speed), rotationComp(), isRed ? -800 : 800)) {
                    newState(STATE_CAP_TURN1);
                }
                break;

            case STATE_CAP_TURN1:
                beaconEnabled = false;
                targetHeading = isRed ? 45 : 310;
                MecanumDrive(0, 0, rotationComp(), 0);
                newState(STATE_CAP_DRIVE1);
                break;

            case STATE_CAP_DRIVE1:
                speed = 1;
                if (MecanumDrive(speed, isRed ? backwardMove(speed) : forwardMove(speed), rotationComp(), isRed ? 8000 : -10250)) {
                    MecanumDrive(0, 0, rotationComp(), 0);
                    newState(isRed ? STATE_DRIVE_FOR_PARK_RED : STATE_TURN_FOR_PARK);
                }
                break;

            case STATE_DRIVE_FOR_PARK_RED:
                if (MecanumDrive(speed, forwardMove(speed), rotationComp(), -1350)) {
                    MecanumDrive(0, 0, rotationComp(), 0);
                    newState(STATE_TURN_FOR_PARK);
                }
                break;

            case STATE_TURN_FOR_PARK:
                speed = 1;
                targetHeading = isRed ? 0 : 270;
                MecanumDrive(0, 0, rotationComp(), 0);
                if (gyroCloseEnough(3))
                    newState(STATE_DRIVE_FOR_PARK);
                break;

            case STATE_DRIVE_FOR_PARK:
                speed = 1;
                if (MecanumDrive(speed, backwardMove(speed), rotationComp(), isRed ? 1800 : 2000)) {
                    MecanumDrive(0, 0, rotationComp(), 0);
                    newState(STATE_STOP);
                }
                break;

            case STATE_STOP:
                MecanumDrive(0, 0, 0, 0);
                break;
        }


    }


    private void newState(State newState) {
        // Reset the state time, and then change to next state.
        mStateTime.reset();
        mCurrentState = newState;
        telemetry.addData("State", mCurrentState);
    }

    public boolean gyroCloseEnough(double epsilon) {
        return Math.abs(currentGyroHeading - targetHeading) < epsilon;
    }
}




