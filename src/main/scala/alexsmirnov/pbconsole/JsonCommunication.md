### How things are encoded in JSON

- **name** is a JSON key (aka **token**) describing a single datum or group of data values 
  - Example: `xfr` referring to the X axis maximum feed rate
  - Example: `x` referring to all values associated with the X axis (the X axis group)
  - Names are not case sensitive
- **value** is a number, a quoted string, true/false, or null (as per JSON spec)
  - True and false values can be `true` and `false` or `t` and `f` for short
  - NULL values can be `null` (case insensitive), or simply `n` for short
  - Null values are GETs, all others will set the value, or in some cases invoke an action
  - A null value in a response indicates that the value in invalid (e.g trying to read a switch that is not configured)
- **NVpair** is a name:value pair or NV pair
- **group** is a collection of one or more NV pairs
  - Groups are used to specify all parameters for a motor, an axis, a PWM channel, or other logical grouping
  - A group is similar in concept to a RESTful resource or composite.

### What is encoded in JSON

    Term | Description
    ---------------|--------------
    **config** | A config is a static configuration setting for some aspect of the machine. These parameters are not changed by Gcode execution (but see the G10 exception). {`xfr:1000}` is an example of a config. So is `{1po:1}`. So is the X group: `{x:n}`.
    **block** | Gcode blocks are lines of gcode consisting of one or more gcode words, optional comments and possibly gcode messages
    **word** | Gcode words make up gcode commands. `G1` is an example of a gcode word. So is `x23.43`
    **comment** | A Gcode comment is denoted by parentheses - `(this is a gcode comment)`
    **JSON active comment** | A [JSON active comment](JSON-Active-Comments) is a way to insert JSON in a Gcode stream
    **message** | A **Gcode message** is a special form of active comment that is echoed to the machine operator. It's the part of the comment that follows a `(msg` preamble. For example: `(msgThis part is echoed to the user)`

## JSON Overview & TinyG Subset

The concise JSON language definition is [here](http://json.org). [Jsonlint](http://jsonlint.com) is a handy JSON validator that you can use to check your requests or responses.

TinyG implements a subset of JSON with the following limitations: 

* Supports 7 bit ASCII characters only 
* Supports decimal numbers only (no hexadecimal numbers or other non-decimals)
* Arrays are returned but are not (yet) accepted as input
* Names (keys) are case-insensitive and cannot be more than 5 characters
* Groups cannot contain more than 24 elements (name/value pairs)
* JSON input objects cannot exceed 254 characters in total length. Outputs can be up to 512 chars.
* Limited object nesting is supported (you won't typically see more than 3 levels)
* All JSON input and output is on a single text line. There is only one `<LF>`, it's at the end of the line (broken lines are not supported)

##JSON Request and Response Formats
JSON requests are used to perform the following actions {with examples}

* Return the value of a single setting or state variable `{"1mi":n}`
* Return the values of a group of settings or state variables (aka a Resource) `{"1":n}`
* Set a single setting or state variable (note that many state variables are read-only) `{"1mi":8}`
* Set a multiple settings or state variables in a group `{"1":{"po":1,"mi":8}}`
* Submit a block (line) of Gcode to perform any supported Gcode command `{"gc":"n20g1f350 x23.4 y43.2"}`
* Special functions and actions;
 * Request a status report `{"sr":n}`
 * Set status report contents `{"sr":{"line":true,"posx":true,posy":true,   ...}}`
 * Run self tests `{"test":1}`
 * Reset parameters to defaults `{"defa":true}`

JSON responses to commands are in the following general form.
<pre>
{"xjm":n} returns:
{"r":{"xjm":5000000000.000},"f":[3,0,6]}

{"2":n} returns:
{"r":{"2":{"ma":1,"sa":1.800,"tr":36.540,"mi":8,"po":1,"pm":1}},"f":[3,0,6]}
</pre>

The `r` is the response envelope. The body of the response is the result returned. In the case of a single name it returns the value. In the case of a group it returns the entire group as a child object. The `f` is the footer which is an array consisting of (1) revision number, (2) status code, (3) the number of lines available in the line buffers.

###Status Reports
Status reports are generated automatically by the system and are therefore asynchronous. JSON reports are in the following general form:
<pre>
{"sr":{"line":0,"posx":0.000,"posy":0.000,"posz":0.000,"posa":0.000,"vel":0.000,"momo":1,"stat":3}}
</pre>

It's similar to a response except there is no header or footer element. Since it's asynchronous the status code is irrelevant, as is the number of lines available in the buffer (0). The exception is a status report that is requested, which will return a footer. E.g. the the command `{sr:n}` can be used to request the status report in the above example. Look here for details of the [status reports](Status-Reports)

###Exception Reports
Exception reports are generated by the system when something wrong is detected. 
The information provided is:
- `fb` firmware build number
- `st` status code of the exception
- `msg` a displaytable, human readbale message describing the exception

Exception reports are in the following general form:
<pre>
{"er":{"fb":100.10,"st":29,"msg":"Generic exception report - bogus exception report"}}
</pre>

## Linemode Protocol

We designed the _linemode protocol_ to help prevent the serial buffer from either filling completely (preventing time-critical commands from getting through) while keeping the serial buffer full enough in order to prevent degradation to motion quality due to the motion commands not making it to the machine in a timely manner.

The protocol is simple - "blast" 4 lines to the board without waiting for responses. From that point on send a single line for every `{r:...}` response received. Every command will return one and only one response. **The exceptions are single character commands, such as !, ~, or ENQ, which do not consume line buffers and do not generate responses.**

In implementation it's actually rather simple:

1. Prepare or start reading the list of _data_ lines to send to the g2core. We'll call this list `line_queue`.
2. Set `lines_to_send` to `4`.
  * `4` has been determined to be a good starting point. This is subject to tuning, and might be adjusted based on your results.
3. Send the first `lines_to_send` lines of `line_queue` to the g2core, decrementing `lines_to_send` by one for _each line sent_.
  * If you need to read more lines into `line_queue`, do so as soon as `lines_to_send` is zero.
  * If `line_queue` is being filled by dynamically generated commands then you can send up to `lines_to_send` lines immedately.
  * Don't forget to decrement `lines_to_send` for _each line sent_! And don't send more than `lines_to_send` lines!
4. When a `{r:...}` response comes back from the g2core, add one to `lines_to_send`.
  * It's **vital** that when any `{r:...}` comes back that `lines_to_send`. If one is lost or ignored then the system will get out of sync and sending will stall.
5. Loop back to 3. (Better yet, have 3 and 4 each loop in their own thread or context.)

Notes:
* Steps 3 and 4 are best to run in their own threads or context. (In node we have each in event handlers, for example.) If that's not practical, it's vital that when a `{r:...}` comes in that `lines_to_send` is incremented and that lines can be sent as quickly after that as possible.
* It is possible (and common) to get two or more `{r:...}` responses before you send another line. This is why it's vital to keep track of `lines_to_send`.
* Note that only _data_ (gcode) lines go into `line_queue`! For configuration JSON or single-line commands, they are sent immediately.
  * It's important to maintain `lines_to_send` even when sending past the `line_queue`.
    * Single-character commands will *not* generate a `{r:...}` response (they may generate other output, however), so there's nothing to do (see following notes). 
    * JSON commands (`{` being the first character on the line) **will** have a `{r:...}` response, so when you send one of those past the queue you should still subtract one from `lines_to_send`, or `lines_to_send` will get out of sync and the sender will eventually stall waiting for responses. This is the *only* case where `lines_to_send` may go negative.
  * Note that control commands, like dta commands, must start at the beginning of a line, so you should always send whole lines. IOW, don't interrupt a line being sent from the `line_queue` to send a JSON command or feedhold `!`.
* **All** communications to the g2core **must** go through this protocol. It's not acceptable to occasionally send a JSON command or gcode line directly past this protocol, or `lines_to_send` will get out of sync and the sender will eventually stall waiting for responses.
