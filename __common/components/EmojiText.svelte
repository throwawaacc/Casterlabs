<script>
    import { onMount, onDestroy } from "svelte";
    import App from "../app.mjs";

    export let escapeHtml = true;
    export let forceProvider = null;

    const unregister = [];

    let emojiProvider = "system";

    let slotElement;
    let text;
    let content;

    function render() {
        if (typeof window != "undefined" && emojiProvider != "system") {
            fetch(
                `https://api.casterlabs.co/v3/emojis/detect?provider=${forceProvider || emojiProvider}&escape=${escapeHtml}`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify({
                        text: text,
                        responseFormat: "HTML",
                    }),
                }
            )
                .then((response) => response.text())
                .then((text) => {
                    content = text;
                });
        } else {
            content = null;
        }
    }

    onMount(() => {
        if (window.Caffeinated) {
            unregister.push(
                App.on("emojiProvider", (v) => {
                    emojiProvider = v;
                })
            );
        }
    });

    onDestroy(() => {
        for (const un of unregister) {
            try {
                App.off(un[0], un[1]);
            } catch (ignored) {}
        }
    });

    $: slotElement, (text = slotElement?.innerText.replace(/:/g, "&#58;"));

    // Rerender on emoji provider change
    $: emojiProvider, render();
    $: text, render();
</script>

<!-- Yoink the text value from svelte -->
<span bind:this={slotElement} style={content ? "display: none;" : ""}>
    <slot />
</span>

{#if content}
    {@html content}
{/if}
