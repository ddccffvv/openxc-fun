

input = open("driving.json")
output = open("filtered-driving.json","w")

counter = 0

for line in input:
    if counter % 3 == 0:
        output.write(line)
    counter += 1
