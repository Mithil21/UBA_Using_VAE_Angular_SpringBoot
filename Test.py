import requests
from bs4 import BeautifulSoup

def print_unicode_grid(doc_url):
    response = requests.get(doc_url)
    response.raise_for_status()
    html = response.text

    soup = BeautifulSoup(html, 'html.parser')
    cells = soup.find_all('td')

    # Skip header row
    data = []
    for i in range(3, len(cells), 3):  # start from index 3 to skip headers
        x = int(cells[i].get_text(strip=True))
        char = cells[i+1].get_text(strip=True)
        y = int(cells[i+2].get_text(strip=True))
        data.append((x, y, char))

    max_x = max(d[0] for d in data)
    max_y = max(d[1] for d in data)

    grid = [[' ' for _ in range(max_x + 1)] for _ in range(max_y + 1)]

    for x, y, char in data:
        grid[y][x] = char

    for row in grid:
        print(''.join(row))


print_unicode_grid('https://docs.google.com/document/d/e/2PACX-1vRPzbNQcx5UriHSbZ-9vmsTow_R6RRe7eyAU60xIF9Dlz-vaHiHNO2TKgDi7jy4ZpTpNqM7EvEcfr_p/pub')