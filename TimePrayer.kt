
import java.time.LocalDate
import kotlin.collections.HashMap

/*
  class calcul des heurs de priere en language KOTLIN

-------------------- Copyright Block ----------------------
 ------ Prayer Times Calculator (ver 1.0)
Copyright (C) 2018
Kotlin Code: Abdelkafi Abdelbasset
Original js Code: Hamid Zarrabi-Zadeh
License: GNU LGPL v3.0
TERMS OF USE:
	Permission is granted to use this code, with or
	without modification, in any website or application
	provided that credit is given to the original work
	with a link back to PrayTimes.org.
This program is distributed in the hope that it will
be useful, but WITHOUT ANY WARRANTY.
PLEASE DO NOT REMOVE THIS COPYRIGHT BLOCK.
--------------------- Help and Manual ----------------------
------------------------ User Interface -------------------------
	getTimes (date, coordinates, timeZone [, dst [, timeFormat]])
	setMethod (method)       // set calculation method
	adjust (parameters)      // adjust calculation parameters
	tune (offsets)           // tune times by given offsets
	getMethod_ ()             // get calculation method
	getSetting ()            // get current calculation parameters
	getOffsets ()            // get current time offsets

------------------------- Example Usage --------------------------

   var h : TimePrayer=TimePrayer("MWL")
   var times = h.getTimes(LocalDate.now(), listOf(35.691,-0.642), 1.0)

   for (i in h.timeNames.keys) {
        println(h.timeNames[i] + " -> "+times[i])}
 */

class TimePrayer{
    // Time Names
    val timeNames = linkedMapOf(
            "imsak"    to "Imsak",
            "fajr"     to "Fajr",
            "sunrise"  to "Sunrise",
            "dhuhr"    to "Dhuhr",
            "asr"      to "Asr",
            "sunset"   to "Sunset",
            "maghrib"  to "Maghrib",
            "isha"     to "Isha",
            "midnight" to "Midnight")

    val listOfMethod = listOf("MWL", "ISNA", "Egypt", "Makkah", "Karachi", "Tehran", "Jafari")



    //--------------------- Default Settings --------------------
    private var calcMethod = "MWL"

    // do not change anything here; use adjust method instead
    private var	settings = hashMapOf<String,Any>(
            "imsak"    to "10 min",
            "dhuhr"    to "0 min",
            "asr"      to "Standard",
            "highLats" to "NightMiddle")

    private var timeFormat = "24h"
    private val timeSuffixes = listOf("am", "pm")
    private val invalidTime =  "-----"
    private var timeZone : Double=0.00
    private var jDate : Double=0.00

    private var offset = hashMapOf<String,Double>()

    private var lat : Double =0.00
    private var lng : Double = 0.00
    private var elv : Double = 0.00

    // ----------- init
    init {

    }
    // ------- constructor
    constructor (method : String){

        if (listOfMethod.contains(method)) this.calcMethod = method
        else this.calcMethod = "MWL"
        // init setting
        this.settings = get_Seting(this.calcMethod)
        // init offset
        for ( key in this.timeNames.keys)
        {offset[key]=0.00}

    }

    fun SetMethod( method : String){
        if (listOfMethod.contains(method)) this.calcMethod = method
        else this.calcMethod = "MWL"
        this.settings = get_Seting(this.calcMethod)
    }

    fun getMethod() : String  {return this.calcMethod  }

    fun getSettings_():HashMap<String, Any> {return this.settings}

    fun tune(timeOffsets :  HashMap<String,Double>) {
        this.offset = timeOffsets
    }

    fun getOffsets():HashMap<String, Double>  { return this.offset}

    // return prayer times for a given date

    fun getTimes(date1 : LocalDate, coords : List<Double>, timezone : Double, dst: Boolean = false, format:String? = null):HashMap<String,Any> {
        this.lat = coords[0]
        this.lng = coords[1]
        if (coords.size>2) { this.elv = coords[2]} else  { this.elv = 0.00}
        if (format != null) {this.timeFormat = format}
        if (dst) {  this.timeZone = timezone + 1} else {  this.timeZone = timezone}

        this.jDate = this.julian(date1.year, date1.monthValue, date1.dayOfMonth) - this.lng / (15 * 24.0)
        return this.computeTimes()
    }


    // convert float time to the given format (see timeFormats),
    private fun getFormattedTime(time1: Double, format: String, suffixes:List<String>? = null):Any {
        //if math.isnan(time):
        //return self.invalidTime
        if (format == "Float") { return time1  }
        var suffixes1 : List<String>?
        if (suffixes == null) { suffixes1 = this.timeSuffixes}
        else { suffixes1 = suffixes}

        var time = this.fixhour(time1 + 0.5 / 60)  // add 0.5 minutes to round
        var hours = Math.floor(time)

        var minutes = Math.floor((time - hours) * 60)

        var suffix : String = ""
        if (format == "12h")
        { if (hours < 12) { suffix = suffixes1[0]}
        else { suffix = suffixes1[1]}
        }
        var formattedTime:String
        var h = "%02d".format(hours.toInt())
        var m = "%02d".format(minutes.toInt())

        if (format !== "24h") { h=   "%02d".format(((hours+11)%12+1).toInt())}
        formattedTime = h+":"+ m
        return formattedTime + suffix
    }

    //---------------------- Calculation Functions -----------------------

    // compute mid-day time
    private  fun midDay( time: Double):Double {
        var eqt = this.sunPosition(this.jDate + time)[1]
        return this.fixhour(12 - eqt)
    }


    // compute the time at which sun reaches a specific angle below horizon
    private fun sunAngleTime( angle : Double, time : Double, direction:String? = null):Double {
        try {
            var decl = this.sunPosition(this.jDate + time)[0]
            var noon:Double = this.midDay(time)
            var t = 1 / 15.0 * this.arccos((-this.sin(angle) - this.sin(decl) * this.sin(this.lat)) /
                    (this.cos(decl) * this.cos(this.lat)))
            if (direction == "ccw") { return noon -t }
            else { return noon + t}
        }
        catch(e : NumberFormatException ) {
            return 0.0
        }
    }

    // compute asr time
    private  fun asrTime( factor: Double, time: Double):Double {
        var decl = this.sunPosition(this.jDate + time)[0]
        var angle = -this.arccot(factor + this.tan(Math.abs(this.lat - decl)))
        return this.sunAngleTime(angle, time)
    }


    //compute declination angle of sun and equation of time
    //Ref: http://aa.usno.navy.mil/faq/docs/SunApprox.php
    private  fun sunPosition(jd:Double):DoubleArray {
        var D = jd - 2451545.0
        var g = this.fixangle(357.529 + 0.98560028 * D)
        var q = this.fixangle(280.459 + 0.98564736 * D)
        var L = this.fixangle(q + 1.915 * this.sin(g) + 0.020 * this.sin(2 * g))

        //var R = 1.00014 - 0.01671 * this.cos(g) - 0.00014 * this.cos(2 * g)
        var e = 23.439 - 0.00000036 * D

        var RA = this.arctan2(this.cos(e) * this.sin(L), this.cos(L)) / 15.0
        var eqt = q / 15.0 - this.fixhour(RA)
        var decl = this.arcsin(this.sin(e) * this.sin(L))

        return doubleArrayOf(decl, eqt)
    }

    //---------------------- Compute Prayer Times -----------------------

    // compute prayer times at given julian date
    private fun computePrayerTimes( times1: HashMap<String,Double>):HashMap<String,Double> {
        var times = this.dayPortion(times1)
        var params = this.settings

        var imsak = this.sunAngleTime(this.eval(params["imsak"].toString()), times["imsak"]!!, "ccw")
        var fajr = this.sunAngleTime(this.eval(params["fajr"].toString()), times["fajr"]!!, "ccw")
        var sunrise = this.sunAngleTime(this.riseSetAngle(this.elv), times["sunrise"]!!, "ccw")
        var dhuhr = this.midDay(times["dhuhr"]!!)
        var asr = this.asrTime(this.asrFactor(params["asr"]!!.toString()).toDouble(), times["asr"]!!)
        var sunset = this.sunAngleTime(this.riseSetAngle(this.elv), times["sunset"]!!)
        var maghrib = this.sunAngleTime(this.eval(params["maghrib"].toString()), times["maghrib"]!!)
        var isha = this.sunAngleTime(this.eval(params["isha"].toString()), times["isha"]!!)
        var reslt= hashMapOf<String,Double> (

                "imsak" to imsak, "fajr" to fajr, "sunrise" to sunrise, "dhuhr" to dhuhr,
                "asr" to asr, "sunset" to sunset, "maghrib" to maghrib, "isha" to isha)
        return reslt
    }

    // compute prayer times
    private  fun computeTimes():HashMap<String,Any> {
        var times = hashMapOf<String,Double>(
                "imsak" to 5.0, "fajr" to 5.0, "sunrise" to 6.0, "dhuhr" to 12.0,
                "asr" to 13.0, "sunset" to 18.0, "maghrib" to 18.0, "isha" to 18.0
        )
        // main iterations
        // for i in range(this.numIterations):
        times = this.computePrayerTimes(times)
        times = this.adjustTimes(times)
        //add midnight time
        if (this.settings["midnight"] == "Jafari")
        {times["midnight"] = times["sunset"]!! + this.timeDiff(times["sunset"]!!, times["fajr"]!!) / 2}
        else
        {times["midnight"] = times["sunset"]!! + this.timeDiff(times["sunset"]!!, times["sunrise"]!!) / 2}

        times = this.tuneTimes(times)
        return this.modifyFormats(times)
    }
    // adjust times in a prayer time array

    private   fun adjustTimes( times : HashMap<String,Double>): HashMap<String,Double> {
        var params = this.settings
        var time1 = times
        var tzAdjust = this.timeZone - this.lng / 15.0
        for (t in times.keys){
            time1[t] = (times[t] as Double) + tzAdjust}

        if (params["highLats"] != null)
        {time1 = this.adjustHighLats(time1)}

        if (this.isMin(params["imsak"]!!))
        {time1["imsak"] = time1["fajr"]!! - this.eval(params["imsak"].toString()) / 60.0}
        // need to ask about 'min' settings
        if  ( this.isMin(params["maghrib"]!!))
        {time1["maghrib"] = time1["sunset"]!! - this.eval(params["maghrib"].toString()) / 60.0}

        if (this.isMin(params["isha"]!!))
        { time1["isha"] = time1["maghrib"]!! - this.eval(params["isha"].toString()) / 60.0}
        time1["dhuhr"] = time1["dhuhr"]!! + this.eval(params["dhuhr"].toString()) / 60.0

        return time1
    }

    // get asr shadow factor
    private  fun asrFactor(asrParam: String) : Double
    {
        // methods = { 'Standard': 1, 'Hanafi': 2 }
        //return methods[asrParam] if asrParam in methods else self.eval(asrParam)
        when(asrParam){
            "Standard"-> return  1.0
            "Hanafi"-> return  2.0
            else-> return  this.eval(asrParam)
        }
    }

    // return sun angle for sunset/sunrise
    private fun riseSetAngle( elevation : Double = 0.0): Double {
        //elevation = 0 if elevation == None else elevation
        return 0.833 + 0.0347 * Math.sqrt(elevation) // an approximation
    }

    // apply offsets to the times
    private  fun tuneTimes(times: HashMap<String,Double>): HashMap<String,Double> {
        for (name in times.keys){
            times[name]!!.plus(this.offset[name]!!.div(60.0))}
        return times
    }
    ///  math

    private  fun julian( ayear : Int, amonth : Int,  aday : Int) : Double
    {
        var year : Double = ayear.toDouble()
        var month : Double = amonth.toDouble()
        var day : Double = aday.toDouble()

        if (month <=2) { year -=1
            month +=12 }

        val a = Math.floor(year / 100)
        val b = 2 - a + Math.floor(a / 4)
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5

    }
    //convert times to given time format
    private fun modifyFormats( times:HashMap<String,Double>):HashMap<String,Any> {
        var xx1 = HashMap<String,Any>()

        for (name in times.keys) {
            xx1[name] = this.getFormattedTime(times[name] as Double, this.timeFormat)
        }
        return xx1
    }

    // adjust times for locations in higher latitudes
    private  fun adjustHighLats( times:HashMap<String,Double>):HashMap<String,Double> {
        var params = this.settings
        var nightTime = this.timeDiff(times["sunset"], times["sunrise"]) // sunset to sunrise
        times["imsak"] = this.adjustHLTime(times["imsak"], times["sunrise"], this.eval(params["imsak"].toString()), nightTime, "ccw")
        times["fajr"] = this.adjustHLTime(times["fajr"], times["sunrise"], this.eval(params["fajr"].toString()), nightTime, "ccw")
        times["isha"] = this.adjustHLTime(times["isha"], times["sunset"], this.eval(params["isha"].toString()), nightTime)
        times["maghrib"] = this.adjustHLTime(times["maghrib"], times["sunset"], this.eval(params["maghrib"].toString()), nightTime)
        return times
    }

    // adjust a time for higher latitudes
    private  fun adjustHLTime(time: Double?, base: Double?, angle:Double?, night: Double?, direction:String? = null):Double {
        var portion = this.nightPortion(angle!!, night!!)
        var time1 = time
        var diff:Double
        if (direction == "ccw") { diff = this.timeDiff(time1, base)}
        else { diff = this.timeDiff(base, time1)}

        if (( time==null) or (diff > portion)) {

            if (direction == "ccw") { time1 = base!! -portion}
            else { time1 = base!! +portion}
        }

        return time1!!
    }


    // the night portion used for adjusting times in higher latitudes

    private  fun nightPortion(angle: Double, night: Double):Double {
        var method = this.settings["highLats"]
        var portion = 1 / 2.0 // midnight
        if (method == "AngleBased") {portion = 1 / 60.0 * angle}
        if (method == "OneSeventh") {portion = 1 / 7.0}
        return portion * night
    }
    // convert hours to day portions
    private  fun dayPortion(times:HashMap<String,Double>):HashMap<String,Double> {
        for (i in times.keys) {
            times[i] = times[i] !!.div(24.0)
        }
        return times
    }

    // method get parar

    protected fun get_Seting(vmethod : String):HashMap<String, Any>  {
        val methods_MWL = hashMapOf(
                "name" to "Muslim World League",
                "fajr" to  18,
                "maghrib" to "0 min",
                "midnight" to "Standard",
                "isha" to 17)

        val methods_ISNA = hashMapOf(
                "name" to "Islamic Society of North America (ISNA)",
                "fajr" to  15,
                "maghrib" to "0 min",
                "midnight" to "Standard",
                "isha" to 15)
        val methods_Egypt = hashMapOf(
                "name" to "Egyptian General Authority of Survey",
                "fajr" to  19.5,
                "maghrib" to "0 min",
                "midnight" to "Standard",
                "isha" to 17.5)
        val methods_Makkah = hashMapOf(
                "name" to "Umm Al-Qura University, Makkah",
                "fajr" to  18.5,
                "maghrib" to "0 min",
                "midnight" to "Standard",
                "isha" to "90 min")
        val methods_Karachi = hashMapOf(
                "name" to "University of Islamic Sciences, Karachi",
                "fajr" to  18,
                "maghrib" to "0 min",
                "midnight" to "Standard",
                "isha" to 18)
        val methods_Tehran = hashMapOf(
                "name" to "Institute of Geophysics, University of Tehran",
                "fajr" to  17.7,
                "isha" to 14,
                "maghrib" to 4.5,
                "midnight" to "Jafari")
        val methods_Jafari = hashMapOf(
                "name" to "Shia Ithna-Ashari, Leva Institute, Qum",
                "fajr" to  16,
                "isha" to 14,
                "maghrib" to 4,
                "midnight" to "Jafari")

        var t : HashMap<String, Any>
        var	def_settings = hashMapOf<String, Any>(
                "imsak"    to "10 min",
                "dhuhr"    to "0 min",
                "asr"      to "Standard",
                "highLats" to "NightMiddle")

        when (vmethod){
            "MWL" ->    {t = methods_MWL}
            "ISNA" ->   {t = methods_ISNA}
            "Egypt"->   {t= methods_Egypt}
            "Makkah"->  {t= methods_Makkah}
            "Karachi"-> {t= methods_Karachi}
            "Tehran"->  {t= methods_Tehran}
            "Jafari"->  {t= methods_Jafari}
            else-> {t = methods_MWL}
        }

        //

        for ((key, value) in t) {
            def_settings[key] = value
        }

        return def_settings
    }
    //---------------------- Misc Functions -----------------------

    // compute the difference between two times
    private  fun timeDiff(time1: Double?, time2: Double?):Double
    {return this.fixhour(time2!!- time1!!)}

    // convert given string into a number
    private  fun eval( st : String?):Double {
        var h : List<String> = st!!.split("[^0-9.+-]")
        var val1:String = h[0]
        if (val1.toDoubleOrNull()==null) {return  0.0}
        else {return val1.toDouble()}
    }

    // detect if input contains 'min'
    private  fun isMin(arg : Any): Boolean {
        var b :Boolean=false
        if (arg is String) {
            if (arg .contains("min"))  {b = true}
        }
        return b
        //return (arg is String) and ((arg as String).contains("min"))
    }

    //----------------- Degree-Based Math Functions -------------------

    private  fun sin(d : Double):Double{ return Math.sin(Math.toRadians(d))}
    private  fun cos(d: Double):Double {return Math.cos(Math.toRadians(d))}
    private  fun tan(d: Double):Double {return Math.tan(Math.toRadians(d))}

    private  fun arcsin(x: Double):Double {return Math.toDegrees(Math.asin(x))}
    private fun arccos(x: Double):Double {return Math.toDegrees(Math.acos(x))}
    private fun arctan(x: Double):Double {return Math.toDegrees(Math.atan(x))}

    private fun arccot(x: Double):Double {return Math.toDegrees(Math.atan(1.0/x))}
    private fun arctan2(y: Double, x: Double):Double {return Math.toDegrees(Math.atan2(y, x))}

    private  fun fixangle(angle: Double):Double {return this.fix(angle, 360.0)}
    private  fun fixhour(hour: Double?):Double {return this.fix(hour!!, 24.0)}

    private fun fix(a: Double, mode: Double): Double {
        var a1 : Double
        // if Math.isnan(a):
        //if (a == null)
        //{ return a }

        a1 = a - mode * (Math.floor(a / mode))
        if (a1<0) { return a1+mode}
        else {return  a1}

    }
}
