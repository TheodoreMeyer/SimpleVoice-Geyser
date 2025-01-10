# Geyser Extension Template
A Geyser Extension template for creating Geyser Extensions. 

## What are Geyser extensions?
Geyser Extensions are the equivalent of “plugins”, but specifically for the Geyser platform. This brings the advantage of them being platform-agnostic, meaning that you won’t have to worry about supporting all platforms individually. Additionally, they will be, by design, only applied for Bedrock players joining via Geyser.

## What can extensions do?
- Register [custom items](https://wiki.geysermc.org/geyser/custom-items/), [custom blocks](https://wiki.geysermc.org/geyser/custom-blocks/#geyser-extensions) and more in code!
- Listen and toggle various Bedrock features (i.e. commands)
- Send forms to Bedrock players using [Cumulus](https://github.com/GeyserMC/Cumulus)
- Listen and interact with [Events](https://wiki.geysermc.org/geyser/events/) 

## GeyserAPI / Geyser Extension docs:
- [Geyser extensions guide](https://wiki.geysermc.org/geyser/extensions/)
- [Geyser API introduction](https://wiki.geysermc.org/geyser/api/)
- [Introduction to Events](https://wiki.geysermc.org/geyser/events/)

## Using this Template
1. Create a new repository using this template
2. Replace the data in `extension.yml` with data relevant to your extension
3. Update the main extension class
4. Run `./gradlew build` to build the extension
5. Copy the built jar from `build/libs` to your Geyser's extensions folder

## Documentation
Our [wiki](https://wiki.geysermc.org/) has helpful articles  

## Coming Soon
- Custom entities
- Resource packs represented in code

## Existing Extensions
See our list [here](https://github.com/GeyserMC/GeyserExtensionList).

## Suggestions?
Reach out on our [Discord](https://discord.gg/geysermc)!

## Important Notes
- `extension.yml` is required for Geyser to load the extension. It must be in the `resources` folder.
- Geyser Extensions: https://github.com/GeyserMC/Geyser/blob/master/api/src/main/java/org/geysermc/geyser/api/extension/Extension.java
- Geyser API docs: https://github.com/GeyserMC/Geyser/blob/master/api/src/main/java/org/geysermc/geyser/api/

