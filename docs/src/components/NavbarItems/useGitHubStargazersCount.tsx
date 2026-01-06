import { useEffect, useState } from "react";

export default function useGitHubStargazersCount(
  repository: string,
): number | null {
  const [stars, setStars] = useState<number | null>(null);
  useEffect(() => {
    (async () => {
      setStars(await retrieveStargazersCount(repository));
    })();
  }, [repository]);

  return stars;
}

async function retrieveStargazersCount(repository: string): Promise<number> {
  var nextRetryInterval = 1000;
  while (true) {
    try {
      const response = await fetch(
        `https://api.github.com/repos/${repository}`,
        {
          headers: {
            Accept: "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
          },
        },
      );
      if (response.ok) {
        const json = await response.json();
        const stargazersCount = json.stargazers_count;
        if (typeof stargazersCount === "number") {
          return stargazersCount;
        }
      }
    } catch {}
    // Wait before re-calling the API.
    await new Promise((resolve) => setTimeout(resolve, nextRetryInterval));
    // Try with a longer interval, but with a 32-second limit.
    nextRetryInterval = Math.min(nextRetryInterval * 2, 32000);
  }
}
