<script>
    import { onDestroy } from "svelte";
    import { Koi } from "../../../routes/app/__layout.svelte";

    import OAuthSignin from "../oauth-signin.mjs";

    const oauth = new OAuthSignin();

    let isSigningIn = false;

    function onClick() {
        if (!isSigningIn) {
            isSigningIn = true;
            oauth
                .start("trovo")
                .then((token) => {
                    localStorage.setItem("cl_studio:usertoken", token);
                    Koi.connect(token);
                })
                .catch(() => {
                    isSigningIn = false;
                });
        }
    }

    onDestroy(() => {
        oauth.cancel();
    });
</script>

<div class="no-select signin-button">
    <button class="button has-text-centered {isSigningIn ? 'is-loading' : ''}" on:click={onClick}>
        <div class="platform-logo">
            <img src="https://cdn.casterlabs.co/services/trovo/icon.svg" alt="Trovo Logo" />
        </div>
        <span> Trovo </span>
    </button>
</div>

<style>
    .button {
        width: 200px;
        margin-top: 1px;
        overflow: hidden;
        color: #dbdbdb;
        transition: 0.5s;
        background-color: #149b53;
    }

    .button:hover {
        transition: 0.5s;
        background-color: #118849;
    }

    .button span {
        z-index: 2;
    }

    .platform-logo {
        position: absolute;
        top: 8px;
        left: 11px;
        width: 23px;
    }
</style>
