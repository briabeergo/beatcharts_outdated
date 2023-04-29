## What is this application?
An application for the Beatstar modding community. Very unfinished, but you are welcome to do whatever you want with it 
But if you do make something with it, please don't forget to mention it in the About App section:
- m1l4q as artist;
- briabeergo as designer;
- iconscout (icons);
- all using libraries.

## Current issues and bugs
- Deluxes are not supported to be converted;
- Score is not calculated correctly;
- Memory leaks during conversion.

# Short introduction to the classes
Utilities and tools:
- Class BeatChartsUtils: Contains everything that can be helpful in the application or have specific data. It's subclasses:
1. class Sys: system API interactions;
2. class Data: data processing, such as formatting text;
3. class SimpleItem: this is a class for a card object with title and text, which I planned to use in many places in the application;
4. class Animations: VERY IMPORTANT that all application animations should be declared there (constraint sets don't count). XML animations should be replaced with these animations;
5. class FilesData: only contains bytes needed for chart conversion files (Maybe there is a better way to use any kind of assets, but it makes no sense. Memory leaks can also be easily fixed with this).

Some important classes:
- class DeEncodingManager: all encoding/decoding (and chart conversion) logic is placed here;
- class SongManager: all song display, copy, delete and other operations are there.

There are more classes and some cpp functions, but you can easily figure out what they do

## What should be deleted
- BackendlessDef: I don't use backendless anymore;
- class ActionPerformed: this notification has been replaced by ActionNotify.
