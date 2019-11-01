fileNames = ['1.txt', '2.txt']

count = 0
for fileName in fileNames:
    with open(fileName, 'r') as f:
        Data = f.readlines()
        count += len(Data)

print("Total Sample Count:", count)
