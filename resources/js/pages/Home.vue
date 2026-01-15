<script setup lang="ts">
import AppWrapper from '@/layouts/AppWrapper.vue';
import { Ref, ref } from 'vue';

const posts: Ref<RedditPost[]> = ref([]);

interface RedditPost {
    author: string,
    title: string,
    selftext: string,
    preview: {
        images: RedditPostImageWrapper[]
    }
}

interface RedditPostImageWrapper {
    source: RedditPostImage,
    resolutions: RedditPostImage[]
}

interface RedditPostImage {
    url: string,
    width: number,
    height: number
}

async function fetchRedditPosts() {
    const response = await (await fetch("https://www.reddit.com/r/AnimeART/hot.json?limit=10")).json();
    for(const post of response.data.children) {
        posts.value.push(post.data);
    }
}

fetchRedditPosts();
</script>

<template>
    <Head title="Home"></Head>

    <AppWrapper :selectedTab="'home'">
        <div class="feed-wrapper">
            <div class="filters">
                <span class="selected">Accepted</span>
                <span>Not decided</span>
                <span>Not accepted</span>
            </div>

            <div class="feed">
                <div class="heading">
                    <h1>Home screen</h1>

                    <div class="stats">
                        <span>1046 undiscovered</span>
                        <span>91278 total</span>
                    </div>
                </div>

                <div class="post" v-for="post in posts" :key="post.title">
                    <h5>u/{{ post.author }}</h5>
                    <h1>{{ post.title }}</h1>
                    <p>{{ post.selftext }}</p>

                    <div class="gallery" v-for="image in post.preview.images" :key="image.source.url" v-if="post.preview != null">
                        <img class="gallery-item" :src="image.source.url.replaceAll('amp;', '')" />
                    </div>
                </div>
            </div>
        </div>
    </AppWrapper>
</template>

<style scoped lang="scss">
    .feed-wrapper {
        flex-grow: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
    }

    .filters {
        padding-top: 1rem;
        padding-inline: 2rem;
        display: flex;
        overflow: scroll;
        gap: 1rem;

        span {
            padding-block: .4rem;
            padding-inline: 1.2rem;
            background-color: rgb(47, 2, 71);
            font-family: "Google Sans Flex", sans-serif;
            font-size: .9em;
            border-radius: .4rem;
            transition: .1s;

            &.selected {
                color: black;
                background-color: white;
            }

            &:not(.selected) {
                cursor: pointer;

                &:hover {
                    opacity: .8;
                }
            }
        }
    }

    .heading {
        margin-block: 1rem;
        display: flex;
        flex-direction: column;
        gap: .1rem;

        h1 {
            font-size: 2em;
            font-family: "Google Sans Flex", sans-serif;
            font-weight: 700;
        }
        
        .stats {
            display: flex;
            justify-content: space-between;
            font-family: "Google Sans Flex", sans-serif;
        }
    }

    .feed {
        display: flex;
        flex-direction: column;
        gap: 1rem;
        padding-block: 1rem;
        padding-inline: 2rem;
        width: 100%;
        max-width: 40rem;

        .post {
            h1 {
                font-size: 1.1em;
                font-weight: 600;
                font-family: "Google Sans Flex", sans-serif;
                margin-top: .2rem;
                margin-bottom: .2rem;
            }

            h5 {
                color: rgb(207, 172, 210);
                font-family: "Google Sans Flex", sans-serif;
            }

            p {
                font-family: "Google Sans Flex", sans-serif;
                max-lines: 3;
                display: -webkit-box;
                -webkit-box-orient: vertical;
                -webkit-line-clamp: 3;
                overflow: hidden;
                text-overflow: ellipsis;
            }

            .gallery {
                width: 100%;
                display: flex;
                flex-wrap: wrap;
                justify-content: center;
                margin-top: .4rem;
                margin-bottom: 1rem;

                .gallery-item {
                    max-height: 40rem;
                    border-radius: 1rem;
                }
            }
        }
    }
</style>