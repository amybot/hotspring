# hotspring

A REST-only* audio server for distributed music for Discord bots.

*except for the part where you need a queue somewhere

Licensed under the GNU GPLv3. (c) amy 2018 - present

## Configuration

Hotspring accepts the following environment variables to configure it:
```Bash
# The hostname of your redis server. Defaults to `127.0.0.1`
REDIS_HOST="localhost"
# The password for your redis server. Required. Defaults to `a`
REDIS_PASS="super-secure-pass"
# The name of the event queue in Redis. Defaults to `event-queue`
EVENT_QUEUE="my-awesome-queue"
```

## Known issues

 - no support for voice server failover
 - no support for region changes
 - no load-balancing support

## Client implementation

This section will be expanded over time.

When sending a REST request to a hotspring server, you need to send a track context:

```JSON
{
    "ctx": {
      "guild_id": "1234567890",
      "channel_id": "1234456790",
      "user_id": "1234567890",
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

- `/connection/close`, `/connection/pause`, `/connection/queue/start`, `/connection/queue/length`, `/connection/track/current`

  Nothing needed.

- `/connection/track/play`, `/connection/queue/add`
  ```JSON
  {
    "url": "https://url.goes.here/whatever"
  }
  ```
  Note that the URL field may be a search string to search a track on YouTube.
  
- `/connection/queue/skip`
  Skip a specific number of songs (n > 0)
  ```JSON
  {
    "skip": 1
  }
  ```
  Skip all songs
  ```JSON
  {
    "skip": -1
  }
  ```

### I heard something about a queue...

Hotspring expects to be able to append events to a Redis queue named `event-queue` by default, and clients are *expected* to
poll this queue for events.

If you don't want to use Redis, feel free to contribute your own queue implementation. 