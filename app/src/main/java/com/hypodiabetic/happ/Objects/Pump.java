package com.hypodiabetic.happ.Objects;

import com.crashlytics.android.Crashlytics;
import com.hypodiabetic.happ.Constants;
import com.hypodiabetic.happ.MainApp;
import com.hypodiabetic.happ.R;
import com.hypodiabetic.happ.tools;

import java.util.Date;

import io.realm.Realm;

/**
 * Created by Tim on 16/02/2016.
 * Pump object provides point in time info of users pump
 */
public class Pump {

    public String   name;                           //name of pump
    public Integer  basal_mode;                     //Basal adjustment mode
    public Integer  min_low_basal_duration;         //low basal duration supported
    public Integer  min_high_basal_duration;        //low basal duration supported
    public Boolean  temp_basal_active=false;        //Is a temp basal active
    public Double   temp_basal_rate;                //Current temp basal rate
    private Integer  temp_basal_percent=null;        //Current temp basal percent
    public Integer  temp_basal_duration;            //Temp duration in Mins
    public Long     temp_basal_duration_left;       //Mins left of this Temp Basal

    private Profile profile;
    private TempBasal tempBasal;

    private static final int ABSOLUTE                   =  1;       //Absolute (U/hr)
    private static final int PERCENT                    =  2;       //Percent of Basal
    private static final int BASAL_PLUS_PERCENT         =  3;       //hourly basal rate plus TBR percentage
    private static final int ABSOLUTE_30MIN_NEG_BASAL   =  4;       //Absolute (U/hr) rate / 2 for 30min rate negative current Basal (Mainly used for Extended Bolus where pump does not support TBR adjustments)

    public Pump(Profile profile, Realm realm){

        this.profile        =   profile;
        tempBasal           =   TempBasal.last(realm);
        name                =   profile.pump_name;

        switch (name){
            case Constants.pump.ROCHE_COMBO:
            case Constants.pump.MEDTRONIC_PERCENT:
            case Constants.pump.TSLIM:
                basal_mode              =   BASAL_PLUS_PERCENT;
                min_low_basal_duration  =   30;
                min_high_basal_duration =   30;
                break;
            case Constants.pump.DANA_R:
                basal_mode              =   BASAL_PLUS_PERCENT;
                min_low_basal_duration  =   60;
                min_high_basal_duration =   30;
                break;
            case Constants.pump.MEDTRONIC_ABSOLUTE:
                basal_mode              =   ABSOLUTE;
                min_low_basal_duration  =   30;
                min_high_basal_duration =   30;
                break;
            case Constants.pump.ANIMAS:
            case Constants.pump.OMNIPOD:
                basal_mode              =   PERCENT;
                min_low_basal_duration  =   30;
                min_high_basal_duration =   30;
                break;
            case Constants.pump.TSLIM_EXTENDED_BOLUS:
                basal_mode              =   ABSOLUTE_30MIN_NEG_BASAL;
                min_low_basal_duration  =   30;
                min_high_basal_duration =   30;
                break;
        }

        temp_basal_active   =   tempBasal.isactive(new Date());
        if (temp_basal_active){
            temp_basal_rate             =   tempBasal.getRate();
            temp_basal_duration         =   tempBasal.getDuration();
            temp_basal_duration_left    =   tempBasal.durationLeft();
        }
    }

    public Integer getTempBasalPercent(){
        if (temp_basal_percent == null) temp_basal_percent = getBasalPercent();
        return temp_basal_percent;
    }

    public Double checkSuggestedRate(Double rate){
        switch (name) {
            case Constants.pump.OMNIPOD:
                //limited to double current basal
                if (rate > (2 * profile.getCurrentBasal())) {
                    return 2 * profile.getCurrentBasal();
                } else {
                    return rate;
                }
            case Constants.pump.TSLIM:
                //limited to 2.5 current basal
                if (rate > (2.5 * profile.getCurrentBasal())) {
                    return 2.5 * profile.getCurrentBasal();
                } else {
                    return rate;
                }
            default:
                return rate;
        }
    }

    public int getSupportedDuration(Double rate){
        if (rate > profile.getCurrentBasal()){
            return min_high_basal_duration;
        } else {
            return min_low_basal_duration;
        }
    }

    public void setNewTempBasal(APSResult apsResult, TempBasal tempBasal){
        temp_basal_active   =   true;
        if (apsResult != null){
            temp_basal_rate             =   apsResult.getRate();
            temp_basal_duration         =   apsResult.getDuration();
            temp_basal_duration_left    =   apsResult.getDuration().longValue();
            if (apsResult.checkIsCancelRequest()) temp_basal_active   =   false;
        } else {
            temp_basal_rate             =   tempBasal.getRate();
            temp_basal_duration         =   tempBasal.getDuration();
            temp_basal_duration_left    =   tempBasal.durationLeft();
            if (tempBasal.checkIsCancelRequest()) temp_basal_active   =   false;
        }
        temp_basal_percent  =   getBasalPercent();
    }

    public String displayCurrentBasal(boolean small){
        if (basal_mode == null) return MainApp.instance().getString(R.string.pump_no_basal_mode);
        String msg="";
        if (small) {
            switch (basal_mode) {
                case ABSOLUTE:
                    msg = tools.formatDisplayBasal(activeRate(), false);
                    break;
                case PERCENT:
                    msg = calcPercentOfBasal() + "%";
                    break;
                case BASAL_PLUS_PERCENT:
                    msg = calcBasalPlusPercent() + "%";
                    break;
                case ABSOLUTE_30MIN_NEG_BASAL:
                    msg = Double.toString((activeRate() / 2) - (profile.getCurrentBasal() / 2)) + " U/30" + MainApp.instance().getString(R.string.min);
                    break;
            }
        } else {
            switch (basal_mode) {
                case ABSOLUTE:
                    msg = tools.formatDisplayBasal(activeRate(), false);
                    break;
                case PERCENT:
                    msg = calcPercentOfBasal() + "% (" + tools.formatDisplayBasal(activeRate(), false) + ")";
                    break;
                case BASAL_PLUS_PERCENT:
                    msg = calcBasalPlusPercent() + "% (" + tools.formatDisplayBasal(activeRate(), false) + ")";
                    break;
                case ABSOLUTE_30MIN_NEG_BASAL:
                    msg = Double.toString((activeRate() / 2) - (profile.getCurrentBasal() / 2)) + " U/30" + MainApp.instance().getString(R.string.min);
                    break;
            }
        }

        if (msg.equals("")){
            Crashlytics.log(1,"APSService","Could not get displayCurrentBasal: " + basal_mode + " " + name);
            return "error";
        } else {
            //if (temp_basal_active) msg = msg + " TBR";
            return msg;
        }
    }

    public String displayTempBasalMinsLeft(){
        if (temp_basal_active){
            if (temp_basal_duration_left > 1){
                return temp_basal_duration_left + " " + MainApp.instance().getString(R.string.mins_left);
            } else {
                return temp_basal_duration_left + " " + MainApp.instance().getString(R.string.min_left);
            }
        } else {
            return "";
        }
    }

    public String displayBasalDesc(boolean small){
        if (small) {
            if (temp_basal_active) {
                if (temp_basal_rate > profile.getCurrentBasal()) {
                    return Constants.ARROW_SINGLE_UP;
                } else {
                    return Constants.ARROW_SINGLE_DOWN;
                }
            } else {
                return "";
            }
        } else {
            if (temp_basal_active) {
                if (temp_basal_rate > profile.getCurrentBasal()) {
                    return MainApp.instance().getString(R.string.high) + " " + getTBRSupport();
                } else {
                    return MainApp.instance().getString(R.string.low) + " " + getTBRSupport();
                }
            } else {
                return getDefaultModeString();
            }
        }
    }

    public String getTBRSupport(){
        switch (name){
            case Constants.pump.TSLIM_EXTENDED_BOLUS:
                return MainApp.instance().getString(R.string.pump_extended_bolus);
            default:
                return MainApp.instance().getString(R.string.pump_tbr);
        }
    }

    public String getDefaultModeString(){
        switch (name){
            case Constants.pump.TSLIM_EXTENDED_BOLUS:
                return MainApp.instance().getString(R.string.pump_no_extended_bolus);
            default:
                return MainApp.instance().getString(R.string.pump_default_basal);
        }
    }

    private int getBasalPercent(){
        if (basal_mode == null) return 0;
        switch (basal_mode){
            case ABSOLUTE:
            case ABSOLUTE_30MIN_NEG_BASAL:
                return 0;
            case PERCENT:
                return calcPercentOfBasal();
            case BASAL_PLUS_PERCENT:
                return calcBasalPlusPercent();
        }
        Crashlytics.log(1,"APSService","Could not get getSuggestedBasalPercent: " + basal_mode + " " + name);
        return 0;
    }

    public Double activeRate(){
        if (temp_basal_active){
            return temp_basal_rate;
        } else {
            return profile.getCurrentBasal();
        }
    }

    private int calcPercentOfBasal(){
        //Change = Suggested TBR - Current Basal
        //% Change = Change / Current Basal * 100
        //Examples:
        //Current Basal: 1u u/hr
        //Low TBR 0.5 u/hr suggested = -50%
        //High TBR 1.5 u/hr suggested = 50%
        if (activeRate() <=0){
            return -100;
        } else {
            Double ratePercent = (activeRate() - profile.getCurrentBasal());
            ratePercent = (ratePercent / profile.getCurrentBasal()) * 100;

            switch (name){
                case Constants.pump.OMNIPOD:
                    //cap at max 100% and round to closet 5
                    if (ratePercent >= 100) {
                        return 100;
                    } else {
                        ratePercent = (double) Math.round(ratePercent / 5) * 5; //round to closest 5
                        return ratePercent.intValue();
                    }
                case Constants.pump.TSLIM:
                    //cap at max 250%
                    if (ratePercent >= 250) {
                        return 250;
                    } else {
                        return ratePercent.intValue();
                    }
                default:
                    return ratePercent.intValue();
            }
        }
    }
    private int calcBasalPlusPercent(){
        Double ratePercent = (activeRate() / profile.getCurrentBasal()) * 100;
        ratePercent = (double) Math.round(ratePercent / 10) * 10; //round to closest 10
        return ratePercent.intValue();
    }

    private String displayBasalMode(){
        if (basal_mode == null) return MainApp.instance().getString(R.string.pump_no_basal_mode);
        switch (basal_mode){
            case ABSOLUTE:
                return MainApp.instance().getString(R.string.pump_basal_absolute);
            case PERCENT:
                return MainApp.instance().getString(R.string.pump_basal_percent);
            case BASAL_PLUS_PERCENT:
                return MainApp.instance().getString(R.string.pump_basal_plus_tbr_percent);
            case ABSOLUTE_30MIN_NEG_BASAL:
                return MainApp.instance().getString(R.string.pump_basal_absolute_30min_neg_basal);
            default:
                return MainApp.instance().getString(R.string.pump_no_basal_mode);
        }
    }

    @Override
    public String toString(){
        return  " name:                     " + name + "\n" +
                " basal_mode:               " + displayBasalMode() + "\n" +
                " min_low_basal_duration:   " + min_low_basal_duration + "\n" +
                " min_high_basal_duration:  " + min_high_basal_duration + "\n" +
                " current_basal_rate:       " + profile.getCurrentBasal() + "\n" +
                " TBR_active:               " + temp_basal_active + "\n" +
                " TBR_rate:                 " + temp_basal_rate + "\n" +
                " TBR_percent:              " + getTempBasalPercent() + "\n" +
                " TBR_duration:             " + temp_basal_duration + "\n" +
                " TBR_duration_left:        " + temp_basal_duration_left;
    }
}
