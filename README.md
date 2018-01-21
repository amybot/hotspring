# hotspring

A distributed, REST-only* audio server for Discord bots.

*except for the part where you need a queue somewhere

## Client implementation

This section will be expanded over time.

When sending a REST request to a hotspring server, you need to send a track context:

```JSON
{
    "ctx": {
      "guild_id": "1234567890",
      "channel_id": "1234456790",
      "bot_id": "1234567890",
      "shard_id": 7 
    },
    "rest of event": "goes here"
}
```
Note that this field will be omitted in all per-endpoint examples.

`channel_id` is ***NOT*** the id of the voice channel, but is the id of the ***TEXT*** channel that the request came from. 
DO NOT MESS THIS UP. If a request didn't come from a text channel for whatever reason, you should provide some default value, 
since this is used in playlist management. No guarantees are made as to what happens when you don't fill this out. 
Here there be monsters. 

---

In addition to the track context, each endpoint may require specific fields to be present:

- `/connection/open`
  ```JSON
  {
    "session": "voice connection session",
    "vsu": {
      "VOICE_SERVER_UPDATE": "object goes here"
    }
  }
  ```

- `/connection/close`, `/connection/pause`

  Nothing needed.

- `/connection/track/play`, `connection/track/queue`
  ```JSON
  {
    "url": "https://url.goes.here/whatever"
  }
  ```
  Note that the URL field may be a search string to search a track on YouTube.