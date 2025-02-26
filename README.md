# my-blog

## WORSE IS BETTER

This is a program I wrote to render my personal blog. You could adapt it to your own needs, if you want, but it's not meant to be a general-purpose solution. I'll keep updating it as long as I'm interested. 

## To-Do List

-- Add comment feature to posts --
- Improve workflow for creating, editing, and publishing a post

### Bike-Shedding List

- Refactor page-rendering pipeline. Currently I have three separate pipelines for rendering posts, standalone pages, and image description pages. I could probably generalize these and pass the relevant info when calling the unified function from (main)
- Ensure columns are limited (80 soft limit 120 hard limit) and lines have correct indentation
- Make a template to avoid hardcoding the "/my-blog" root--instead, this should be passed as an argument so in the case that the website moves it's easier to update. 

## License

Copyright © 2025 Nathaniel Ingle 

Source Code (any file in the src or test directories) is made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

Blog Content (any file in the docs or resources directories) is licensed
under CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/?ref=chooser-v1
